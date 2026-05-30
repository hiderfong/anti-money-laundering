package com.insurance.aml.module.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.ai.mapper.AiRiskScoreRecordMapper;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolItemVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolOverviewVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolQueryRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskScoreRecordVO;
import com.insurance.aml.module.ai.model.entity.AiRiskScoreRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * AI评分记录复核与待复核池服务。
 *
 * <p>承载评分记录的查询/分页/导出、系统弱标签推断、复核登记与处置链路印证，
 * 与评分计算解耦。行为与原 {@code AiRiskScoringServiceImpl} 保持一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRiskReviewService {

    private static final String AUTO_LABEL_TRUE_POSITIVE = "LIKELY_TRUE_POSITIVE";
    private static final String AUTO_LABEL_FALSE_POSITIVE = "LIKELY_FALSE_POSITIVE";
    private static final String AUTO_LABEL_UNCONFIRMED = "UNCONFIRMED";
    private static final String REVIEW_TRUE_POSITIVE = "TRUE_POSITIVE";
    private static final String REVIEW_FALSE_POSITIVE = "FALSE_POSITIVE";
    private static final String REVIEW_NEEDS_MONITORING = "NEEDS_MONITORING";

    private final AiRiskScoreRecordMapper scoreRecordMapper;
    private final JdbcTemplate jdbcTemplate;

    public List<AiRiskScoreRecordVO> recentScores(String subjectType, Long subjectId, Integer limit) {
        String normalizedType = normalizeSubjectType(subjectType);
        int safeLimit = Math.max(1, Math.min(limit == null ? 5 : limit, 50));
        List<AiRiskScoreRecord> records = scoreRecordMapper.selectList(new LambdaQueryWrapper<AiRiskScoreRecord>()
                .eq(AiRiskScoreRecord::getSubjectType, normalizedType)
                .eq(AiRiskScoreRecord::getSubjectId, subjectId)
                .orderByDesc(AiRiskScoreRecord::getScoredAt)
                .orderByDesc(AiRiskScoreRecord::getCreatedTime)
                .last("LIMIT " + safeLimit));
        return records.stream().map(this::toRecordVO).toList();
    }

    public PageResult<AiRiskReviewPoolItemVO> pageReviewPool(AiRiskReviewPoolQueryRequest request) {
        List<AiRiskReviewPoolItemVO> filtered = queryReviewPoolItems(request, 0);

        int page = Math.max(1, request.getPage());
        int size = Math.max(1, Math.min(request.getSize(), 500));
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return PageResult.<AiRiskReviewPoolItemVO>builder()
                .total(filtered.size())
                .list(filtered.subList(from, to))
                .page(page)
                .size(size)
                .totalPages((int) Math.ceil(filtered.size() * 1.0 / size))
                .build();
    }

    public AiRiskReviewPoolOverviewVO reviewPoolOverview() {
        List<AiRiskReviewPoolItemVO> items = scoreRecordMapper.selectList(new LambdaQueryWrapper<AiRiskScoreRecord>()
                        .orderByDesc(AiRiskScoreRecord::getScoredAt))
                .stream()
                .map(this::toReviewPoolItem)
                .toList();
        LocalDateTime latest = items.stream()
                .map(AiRiskReviewPoolItemVO::getScoredAt)
                .filter(time -> time != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return AiRiskReviewPoolOverviewVO.builder()
                .totalScores(items.size())
                .pendingReviewCount(items.stream().filter(this::isPendingReview).count())
                .likelyTruePositiveCount(items.stream().filter(item -> AUTO_LABEL_TRUE_POSITIVE.equals(item.getAutoLabel())).count())
                .likelyFalsePositiveCount(items.stream().filter(item -> AUTO_LABEL_FALSE_POSITIVE.equals(item.getAutoLabel())).count())
                .unconfirmedCount(items.stream().filter(item -> AUTO_LABEL_UNCONFIRMED.equals(item.getAutoLabel())).count())
                .highOrCriticalCount(items.stream().filter(item -> item.getScore() != null && item.getScore() >= 65).count())
                .corroboratedCount(items.stream().filter(item -> item.getVerificationBasis() != null && item.getVerificationBasis().contains("印证")).count())
                .highScoreNoRuleHitCount(items.stream().filter(item -> item.getScore() != null && item.getScore() >= 65 && item.getVerificationBasis() != null && item.getVerificationBasis().contains("暂无规则")).count())
                .lowScoreWithDispositionCount(items.stream().filter(item -> item.getScore() != null && item.getScore() < 35 && AUTO_LABEL_TRUE_POSITIVE.equals(item.getAutoLabel())).count())
                .latestScoredAt(latest)
                .build();
    }

    public AiRiskReviewPoolItemVO reviewScoreRecord(Long recordId, AiRiskReviewRequest request) {
        AiRiskScoreRecord record = scoreRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "AI评分记录不存在，ID：" + recordId);
        }
        String label = normalizeReviewLabel(request.getReviewLabel());
        record.setManualReviewLabel(label);
        record.setManualReviewComment(trimToNull(request.getReviewComment()));
        record.setReviewedBy(firstNonBlank(request.getReviewer(), SecurityUtils.getCurrentUsername(), "system"));
        record.setReviewedAt(LocalDateTime.now());
        scoreRecordMapper.updateById(record);
        log.info("AI评分记录已登记复核结果: recordId={}, label={}, reviewer={}", recordId, label, record.getReviewedBy());
        return toReviewPoolItem(record);
    }

    public byte[] exportReviewPool(AiRiskReviewPoolQueryRequest request) {
        List<AiRiskReviewPoolItemVO> items = queryReviewPoolItems(request, 10000);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0xEF);
        baos.write(0xBB);
        baos.write(0xBF);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.println("评分流水号,主体类型,主体ID,主体名称,AI风险分,风险等级,置信度,系统弱标签,复核状态,优先级,判断依据,主要贡献因子,模型版本,评分时间,人工复核标签,复核人,复核时间,复核备注,跟进任务ID,跟进任务创建人,跟进任务创建时间");
            for (AiRiskReviewPoolItemVO item : items) {
                writer.println(String.join(",",
                        escapeCsv(item.getScoreNo()),
                        escapeCsv(subjectTypeText(item.getSubjectType())),
                        escapeCsv(item.getSubjectId() == null ? "" : String.valueOf(item.getSubjectId())),
                        escapeCsv(item.getSubjectName()),
                        escapeCsv(item.getScore() == null ? "" : String.valueOf(item.getScore())),
                        escapeCsv(item.getRiskLevel()),
                        escapeCsv(item.getConfidence() == null ? "" : String.valueOf(item.getConfidence())),
                        escapeCsv(item.getAutoLabelText()),
                        escapeCsv(reviewStatusText(item.getReviewStatus())),
                        escapeCsv(item.getPriorityLevel()),
                        escapeCsv(item.getVerificationBasis()),
                        escapeCsv(item.getFactorSummary()),
                        escapeCsv(item.getModelVersion()),
                        escapeCsv(item.getScoredAt() == null ? "" : item.getScoredAt().toString()),
                        escapeCsv(item.getManualReviewLabelText()),
                        escapeCsv(item.getReviewedBy()),
                        escapeCsv(item.getReviewedAt() == null ? "" : item.getReviewedAt().toString()),
                        escapeCsv(item.getManualReviewComment()),
                        escapeCsv(item.getFollowUpTaskId() == null ? "" : String.valueOf(item.getFollowUpTaskId())),
                        escapeCsv(item.getFollowUpCreatedBy()),
                        escapeCsv(item.getFollowUpCreatedAt() == null ? "" : item.getFollowUpCreatedAt().toString())
                ));
            }
            writer.flush();
        }
        log.info("导出AI评分待复核清单成功，共{}条记录", items.size());
        return baos.toByteArray();
    }

    public String normalizeSubjectType(String subjectType) {
        String normalized = StringUtils.hasText(subjectType) ? subjectType.trim().toUpperCase(Locale.ROOT) : "";
        if (!List.of("CUSTOMER", "TRANSACTION", "ALERT").contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的AI评分主体类型：" + subjectType);
        }
        return normalized;
    }

    private List<AiRiskReviewPoolItemVO> queryReviewPoolItems(AiRiskReviewPoolQueryRequest request, int maxRows) {
        LambdaQueryWrapper<AiRiskScoreRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getSubjectType()), AiRiskScoreRecord::getSubjectType, request.getSubjectType())
                .eq(StringUtils.hasText(request.getRiskLevel()), AiRiskScoreRecord::getRiskLevel, request.getRiskLevel())
                .eq(StringUtils.hasText(request.getModelCode()), AiRiskScoreRecord::getModelCode, request.getModelCode())
                .ge(request.getMinScore() != null, AiRiskScoreRecord::getScore, request.getMinScore())
                .orderByDesc(AiRiskScoreRecord::getScoredAt)
                .orderByDesc(AiRiskScoreRecord::getCreatedTime);
        if (maxRows > 0) {
            wrapper.last("LIMIT " + maxRows);
        }

        return scoreRecordMapper.selectList(wrapper).stream()
                .map(this::toReviewPoolItem)
                .filter(item -> !StringUtils.hasText(request.getAutoLabel()) || request.getAutoLabel().equals(item.getAutoLabel()))
                .filter(item -> !Boolean.TRUE.equals(request.getPendingOnly()) || isPendingReview(item))
                .toList();
    }

    private AiRiskScoreRecordVO toRecordVO(AiRiskScoreRecord record) {
        return AiRiskScoreRecordVO.builder()
                .id(record.getId())
                .scoreNo(record.getScoreNo())
                .subjectType(record.getSubjectType())
                .subjectId(record.getSubjectId())
                .subjectName(record.getSubjectName())
                .modelId(record.getModelId())
                .modelCode(record.getModelCode())
                .modelName(record.getModelName())
                .modelVersion(record.getModelVersion())
                .score(record.getScore())
                .riskLevel(record.getRiskLevel())
                .confidence(record.getConfidence())
                .factorSummary(record.getFactorSummary())
                .scoredAt(record.getScoredAt())
                .build();
    }

    public AiRiskReviewPoolItemVO toReviewPoolItem(AiRiskScoreRecord record) {
        String autoLabel = inferAutoLabel(record);
        String basis = buildVerificationBasis(record, autoLabel);
        return AiRiskReviewPoolItemVO.builder()
                .id(record.getId())
                .scoreNo(record.getScoreNo())
                .subjectType(record.getSubjectType())
                .subjectId(record.getSubjectId())
                .subjectName(record.getSubjectName())
                .modelCode(record.getModelCode())
                .modelName(record.getModelName())
                .modelVersion(record.getModelVersion())
                .score(record.getScore())
                .riskLevel(record.getRiskLevel())
                .confidence(record.getConfidence())
                .autoLabel(autoLabel)
                .autoLabelText(autoLabelText(autoLabel))
                .reviewStatus(reviewStatus(record, autoLabel))
                .priorityLevel(priorityLevel(record))
                .verificationBasis(basis)
                .factorSummary(record.getFactorSummary())
                .scoredAt(record.getScoredAt())
                .manualReviewLabel(record.getManualReviewLabel())
                .manualReviewLabelText(manualReviewLabelText(record.getManualReviewLabel()))
                .manualReviewComment(record.getManualReviewComment())
                .reviewedBy(record.getReviewedBy())
                .reviewedAt(record.getReviewedAt())
                .followUpTaskId(record.getFollowUpTaskId())
                .followUpCreatedAt(record.getFollowUpCreatedAt())
                .followUpCreatedBy(record.getFollowUpCreatedBy())
                .build();
    }

    private String inferAutoLabel(AiRiskScoreRecord record) {
        if (hasExcludedDisposition(record)) {
            return AUTO_LABEL_FALSE_POSITIVE;
        }
        if (hasConfirmedDisposition(record)) {
            return AUTO_LABEL_TRUE_POSITIVE;
        }
        return AUTO_LABEL_UNCONFIRMED;
    }

    private String autoLabelText(String autoLabel) {
        return switch (autoLabel) {
            case AUTO_LABEL_TRUE_POSITIVE -> "疑似有效风险";
            case AUTO_LABEL_FALSE_POSITIVE -> "疑似误报";
            default -> "尚未确认";
        };
    }

    private String reviewStatus(AiRiskScoreRecord record, String autoLabel) {
        if (StringUtils.hasText(record.getManualReviewLabel())) {
            return "MANUAL_REVIEWED";
        }
        if (AUTO_LABEL_UNCONFIRMED.equals(autoLabel)) {
            return "PENDING_REVIEW";
        }
        if (record.getScore() != null && record.getScore() >= 85) {
            return "DEFERRED_REVIEW";
        }
        return "AUTO_WEAK_LABELED";
    }

    private String reviewStatusText(String reviewStatus) {
        return switch (reviewStatus) {
            case "PENDING_REVIEW" -> "待延后复核";
            case "DEFERRED_REVIEW" -> "高优先级留痕";
            case "AUTO_WEAK_LABELED" -> "系统弱标注";
            case "MANUAL_REVIEWED" -> "已人工确认";
            default -> reviewStatus == null ? "" : reviewStatus;
        };
    }

    private String normalizeReviewLabel(String label) {
        String value = label == null ? "" : label.trim().toUpperCase(Locale.ROOT);
        if (REVIEW_TRUE_POSITIVE.equals(value)
                || REVIEW_FALSE_POSITIVE.equals(value)
                || REVIEW_NEEDS_MONITORING.equals(value)) {
            return value;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "复核标签不支持：" + label);
    }

    private String manualReviewLabelText(String label) {
        return switch (label == null ? "" : label) {
            case REVIEW_TRUE_POSITIVE -> "确认有效风险";
            case REVIEW_FALSE_POSITIVE -> "确认误报";
            case REVIEW_NEEDS_MONITORING -> "继续观察";
            default -> "";
        };
    }

    private String subjectTypeText(String subjectType) {
        return switch (subjectType == null ? "" : subjectType) {
            case "CUSTOMER" -> "客户";
            case "TRANSACTION" -> "交易";
            case "ALERT" -> "预警";
            default -> subjectType == null ? "" : subjectType;
        };
    }

    private boolean isPendingReview(AiRiskReviewPoolItemVO item) {
        return "PENDING_REVIEW".equals(item.getReviewStatus()) || "DEFERRED_REVIEW".equals(item.getReviewStatus());
    }

    private String priorityLevel(AiRiskScoreRecord record) {
        int score = record.getScore() == null ? 0 : record.getScore();
        if (score >= 85 || "CRITICAL".equals(record.getRiskLevel())) {
            return "P0";
        }
        if (score >= 65 || "HIGH".equals(record.getRiskLevel())) {
            return "P1";
        }
        if (score >= 35 || "MEDIUM".equals(record.getRiskLevel())) {
            return "P2";
        }
        return "P3";
    }

    private String buildVerificationBasis(AiRiskScoreRecord record, String autoLabel) {
        if (AUTO_LABEL_FALSE_POSITIVE.equals(autoLabel)) {
            return "处置链路显示已排除或疑似误报，可作为弱负样本观察。";
        }
        if (AUTO_LABEL_TRUE_POSITIVE.equals(autoLabel)) {
            return "处置链路已有确认可疑、案件或STR印证，可作为弱正样本观察。";
        }
        if (hasRuleCorroboration(record)) {
            return "已有规则命中或预警关联印证，但尚缺少最终处置结论。";
        }
        if (record.getScore() != null && record.getScore() >= 65) {
            return "AI高分但暂无规则或处置链路印证，建议后续优先抽查。";
        }
        return "暂无规则或处置链路印证，保持监控观察。";
    }

    private boolean hasConfirmedDisposition(AiRiskScoreRecord record) {
        if ("ALERT".equals(record.getSubjectType()) && record.getAlertId() != null) {
            return countAlert(record.getAlertId(), "process_result = 'CONFIRMED_SUSPICIOUS' OR status = 'CONFIRMED'") > 0;
        }
        if ("TRANSACTION".equals(record.getSubjectType()) && record.getTransactionId() != null) {
            return countRelatedConfirmedAlerts(record.getTransactionId()) > 0 || countCasesOrReports(record.getCustomerId()) > 0;
        }
        return countConfirmedAlertsForCustomer(record.getCustomerId()) > 0 || countCasesOrReports(record.getCustomerId()) > 0;
    }

    private boolean hasExcludedDisposition(AiRiskScoreRecord record) {
        if (!"ALERT".equals(record.getSubjectType()) || record.getAlertId() == null) {
            return false;
        }
        return countAlert(record.getAlertId(), "process_result = 'EXCLUDED' OR status = 'EXCLUDED'") > 0;
    }

    private boolean hasRuleCorroboration(AiRiskScoreRecord record) {
        if (StringUtils.hasText(record.getFactorSnapshotJson())
                && (record.getFactorSnapshotJson().contains("ALERT_RULE_HIT")
                || record.getFactorSnapshotJson().contains("TXN_RELATED_ALERT"))) {
            return true;
        }
        if ("ALERT".equals(record.getSubjectType()) && record.getAlertId() != null) {
            return countAlert(record.getAlertId(), "COALESCE(source_rule_codes, '') <> ''") > 0;
        }
        if ("TRANSACTION".equals(record.getSubjectType()) && record.getTransactionId() != null) {
            return countRelatedAlerts(record.getTransactionId()) > 0;
        }
        return countConfirmedAlertsForCustomer(record.getCustomerId()) > 0;
    }

    private int countAlert(Long alertId, String condition) {
        if (alertId == null) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_alert WHERE id = ? AND (" + condition + ")", Integer.class, alertId);
        return count == null ? 0 : count;
    }

    private int countRelatedAlerts(Long transactionId) {
        if (transactionId == null) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_alert
                WHERE related_transaction_ids LIKE ?
                """, Integer.class, "%" + transactionId + "%");
        return count == null ? 0 : count;
    }

    private int countRelatedConfirmedAlerts(Long transactionId) {
        if (transactionId == null) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_alert
                WHERE related_transaction_ids LIKE ?
                  AND (process_result = 'CONFIRMED_SUSPICIOUS' OR status = 'CONFIRMED')
                """, Integer.class, "%" + transactionId + "%");
        return count == null ? 0 : count;
    }

    private int countConfirmedAlertsForCustomer(Long customerId) {
        if (customerId == null) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_alert
                WHERE customer_id = ?
                  AND (process_result = 'CONFIRMED_SUSPICIOUS' OR status = 'CONFIRMED')
                """, Integer.class, customerId);
        return count == null ? 0 : count;
    }

    private int countCasesOrReports(Long customerId) {
        if (customerId == null) {
            return 0;
        }
        return countForCustomer("t_case", customerId) + countForCustomer("t_str_report", customerId);
    }

    private int countForCustomer(String tableName, Long customerId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE customer_id = ?", Integer.class, customerId);
        return count == null ? 0 : count;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
