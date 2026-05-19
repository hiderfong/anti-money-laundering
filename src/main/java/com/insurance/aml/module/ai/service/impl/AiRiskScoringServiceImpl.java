package com.insurance.aml.module.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.ai.mapper.AiRiskScoreRecordMapper;
import com.insurance.aml.module.ai.model.dto.AiRiskFactorVO;
import com.insurance.aml.module.ai.model.dto.AiRiskFeatureSummaryVO;
import com.insurance.aml.module.ai.model.dto.AiRiskModelStatusVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolItemVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolOverviewVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolQueryRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskScoreRecordVO;
import com.insurance.aml.module.ai.model.dto.AiRiskScoreVO;
import com.insurance.aml.module.ai.model.entity.AiRiskScoreRecord;
import com.insurance.aml.module.ai.service.AiRiskScoringService;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.modelmgmt.mapper.AmlModelMapper;
import com.insurance.aml.module.modelmgmt.model.entity.AmlModel;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.service.TransactionAnomalyDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 可解释AI风险评分基线。
 *
 * <p>v1先以规则化特征评分方式落地，保证结果可解释、可审计；后续可将同一DTO
 * 接到监督学习模型、图模型或外部模型服务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRiskScoringServiceImpl implements AiRiskScoringService {

    private static final String MODEL_CODE = "AI_AML_RISK_BASELINE_V1";
    private static final String MODEL_NAME = "AI可解释风险评分基线模型";
    private static final String MODEL_VERSION = "1.0.0";
    private static final String AUTO_LABEL_TRUE_POSITIVE = "LIKELY_TRUE_POSITIVE";
    private static final String AUTO_LABEL_FALSE_POSITIVE = "LIKELY_FALSE_POSITIVE";
    private static final String AUTO_LABEL_UNCONFIRMED = "UNCONFIRMED";
    private static final String REVIEW_TRUE_POSITIVE = "TRUE_POSITIVE";
    private static final String REVIEW_FALSE_POSITIVE = "FALSE_POSITIVE";
    private static final String REVIEW_NEEDS_MONITORING = "NEEDS_MONITORING";
    private static final int WINDOW_DAYS = 90;
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = BigDecimal.valueOf(50_000);
    private static final BigDecimal VERY_HIGH_AMOUNT_THRESHOLD = BigDecimal.valueOf(200_000);
    private static final BigDecimal WATCHLIST_HIT_THRESHOLD = BigDecimal.valueOf(80);

    private final CustomerMapper customerMapper;
    private final TransactionMapper transactionMapper;
    private final AlertMapper alertMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionAnomalyDetector transactionAnomalyDetector;
    private final AmlModelMapper amlModelMapper;
    private final AiRiskScoreRecordMapper scoreRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AiRiskScoreVO scoreCustomer(Long customerId) {
        Customer customer = findCustomer(customerId);
        AiRiskFeatureSummaryVO features = buildCustomerFeatures(customer);

        List<AiRiskFactorVO> factors = new ArrayList<>();
        addIdentityFactors(factors, customer);
        addKycFactor(factors, features);
        addTransactionBehaviorFactors(factors, features);
        addDispositionFactors(factors, features);
        addRelationshipFactors(factors, features);

        AmlModel model = resolveServingModel();
        AiRiskScoreVO result = buildResult("CUSTOMER", customer.getId(), customer.getName(), features, factors);
        applyModelContext(result, model);
        result.setConfidence(calculateCustomerConfidence(customer, features));
        addCustomerEvidenceAndRecommendations(result, customer, features);
        return persistScoreRecord(result, customer.getId(), null, null, model);
    }

    @Override
    public AiRiskScoreVO scoreTransaction(Long transactionId) {
        Transaction transaction = transactionMapper.selectById(transactionId);
        if (transaction == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "交易不存在，ID：" + transactionId);
        }

        Customer customer = findCustomer(transaction.getCustomerId());
        AiRiskFeatureSummaryVO features = buildCustomerFeatures(customer);
        enrichTransactionFeatures(features, transaction);

        List<AiRiskFactorVO> factors = new ArrayList<>();
        addTransactionAmountFactor(factors, transaction, features);
        addTransactionAttributeFactors(factors, transaction, features);
        addCustomerContextFactors(factors, customer);
        addRelatedAlertFactor(factors, features);

        AmlModel model = resolveServingModel();
        AiRiskScoreVO result = buildResult("TRANSACTION", transaction.getId(), transaction.getTransactionNo(), features, factors);
        applyModelContext(result, model);
        result.setConfidence(calculateTransactionConfidence(transaction, features));
        addTransactionEvidenceAndRecommendations(result, transaction, customer, features);
        return persistScoreRecord(result, transaction.getCustomerId(), transaction.getId(), null, model);
    }

    @Override
    public AiRiskScoreVO scoreAlert(Long alertId) {
        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(ResultCode.ALERT_NOT_FOUND, "预警不存在，ID：" + alertId);
        }

        Customer customer = findCustomer(alert.getCustomerId());
        AiRiskFeatureSummaryVO features = buildCustomerFeatures(customer);
        features.setRelatedAlertCount(countRelatedTransactions(alert.getRelatedTransactionIds()));

        List<AiRiskFactorVO> factors = new ArrayList<>();
        addAlertNativeFactor(factors, alert);
        addCustomerContextFactors(factors, customer);
        addDispositionFactors(factors, features);
        addAlertStatusFactor(factors, alert);

        AmlModel model = resolveServingModel();
        AiRiskScoreVO result = buildResult("ALERT", alert.getId(), alert.getAlertNo(), features, factors);
        applyModelContext(result, model);
        result.setConfidence(calculateAlertConfidence(alert, features));
        addAlertEvidenceAndRecommendations(result, alert, customer, features);
        return persistScoreRecord(result, alert.getCustomerId(), null, alert.getId(), model);
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    public byte[] exportReviewPool(AiRiskReviewPoolQueryRequest request) {
        List<AiRiskReviewPoolItemVO> items = queryReviewPoolItems(request, 10000);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0xEF);
        baos.write(0xBB);
        baos.write(0xBF);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.println("评分流水号,主体类型,主体ID,主体名称,AI风险分,风险等级,置信度,系统弱标签,复核状态,优先级,判断依据,主要贡献因子,模型版本,评分时间,人工复核标签,复核人,复核时间,复核备注");
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
                        escapeCsv(item.getManualReviewComment())
                ));
            }
            writer.flush();
        }
        log.info("导出AI评分待复核清单成功，共{}条记录", items.size());
        return baos.toByteArray();
    }

    @Override
    public AiRiskModelStatusVO getModelStatus() {
        AmlModel model = resolveServingModel();
        AiRiskModelStatusVO status = new AiRiskModelStatusVO();
        applyStatusModelContext(status, model);
        Map<String, Object> stats = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS recordCount, MAX(scored_at) AS lastScoredAt
                FROM t_ai_risk_score_record
                WHERE model_code = ?
                """, MODEL_CODE);
        status.setScoringRecordCount(longValue(stats, "recordCount"));
        Object lastScoredAt = stats.get("lastScoredAt");
        if (lastScoredAt instanceof LocalDateTime time) {
            status.setLastScoredAt(time);
        } else if (lastScoredAt instanceof java.sql.Timestamp timestamp) {
            status.setLastScoredAt(timestamp.toLocalDateTime());
        }
        return status;
    }

    private Customer findCustomer(Long customerId) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException(ResultCode.CUSTOMER_NOT_FOUND, "客户不存在，ID：" + customerId);
        }
        return customer;
    }

    private AiRiskFeatureSummaryVO buildCustomerFeatures(Customer customer) {
        AiRiskFeatureSummaryVO features = new AiRiskFeatureSummaryVO();
        LocalDateTime since = LocalDateTime.now().minusDays(WINDOW_DAYS);

        Map<String, Object> txn = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS transactionCount,
                       COALESCE(SUM(amount), 0) AS totalAmount,
                       COALESCE(MAX(amount), 0) AS maxAmount,
                       SUM(CASE WHEN payment_method = 'CASH' THEN 1 ELSE 0 END) AS cashCount,
                       SUM(CASE WHEN is_cross_border = TRUE THEN 1 ELSE 0 END) AS crossBorderCount,
                       SUM(CASE WHEN amount >= ? THEN 1 ELSE 0 END) AS highAmountCount,
                       COUNT(DISTINCT NULLIF(counterparty_name, '')) AS distinctCounterpartyCount
                FROM t_transaction
                WHERE customer_id = ?
                  AND transaction_time >= ?
                  AND status = 'SUCCESS'
                """, HIGH_AMOUNT_THRESHOLD, customer.getId(), since);
        features.setTransactionCount90d(intValue(txn, "transactionCount"));
        features.setTotalAmount90d(decimalValue(txn, "totalAmount"));
        features.setMaxAmount90d(decimalValue(txn, "maxAmount"));
        features.setCashTransactionCount90d(intValue(txn, "cashCount"));
        features.setCrossBorderTransactionCount90d(intValue(txn, "crossBorderCount"));
        features.setHighAmountTransactionCount90d(intValue(txn, "highAmountCount"));
        features.setDistinctCounterpartyCount90d(intValue(txn, "distinctCounterpartyCount"));

        Map<String, Object> alerts = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS totalAlertCount,
                       SUM(CASE WHEN risk_level IN ('HIGH', 'CRITICAL') THEN 1 ELSE 0 END) AS highRiskAlertCount,
                       SUM(CASE WHEN status IN ('NEW', 'ASSIGNED', 'PROCESSING', 'ESCALATED') THEN 1 ELSE 0 END) AS activeAlertCount,
                       SUM(CASE WHEN process_result = 'CONFIRMED_SUSPICIOUS' THEN 1 ELSE 0 END) AS confirmedCount
                FROM t_alert
                WHERE customer_id = ?
                """, customer.getId());
        features.setActiveAlertCount(intValue(alerts, "activeAlertCount"));
        features.setHighRiskAlertCount(intValue(alerts, "highRiskAlertCount"));
        features.setConfirmedSuspiciousAlertCount(intValue(alerts, "confirmedCount"));

        features.setCaseCount(countForCustomer("t_case", customer.getId()));
        features.setStrReportCount(countForCustomer("t_str_report", customer.getId()));
        features.setWatchlistHitCount(countWatchlistHits(customer.getId()));
        features.setBeneficialOwnerCount(countForCustomer("t_customer_beneficial_owner", customer.getId()));
        features.setControllingOwnerCount(countControllingOwners(customer.getId()));
        features.setKycCompleteness(calculateKycCompleteness(customer));

        return features;
    }

    private void enrichTransactionFeatures(AiRiskFeatureSummaryVO features, Transaction transaction) {
        BigDecimal avgAmount = jdbcTemplate.queryForObject("""
                SELECT COALESCE(AVG(amount), 0)
                FROM t_transaction
                WHERE customer_id = ?
                  AND id <> ?
                  AND transaction_time >= ?
                  AND status = 'SUCCESS'
                """, BigDecimal.class, transaction.getCustomerId(), transaction.getId(), LocalDateTime.now().minusDays(WINDOW_DAYS));
        if (avgAmount != null && avgAmount.compareTo(BigDecimal.ZERO) > 0 && transaction.getAmount() != null) {
            features.setAmountToAverageRatio(transaction.getAmount().divide(avgAmount, 2, RoundingMode.HALF_UP));
        }
        if (StringUtils.hasText(transaction.getCounterpartyAccount())) {
            Integer sharedCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(DISTINCT customer_id)
                    FROM t_transaction
                    WHERE counterparty_account = ?
                      AND customer_id <> ?
                    """, Integer.class, transaction.getCounterpartyAccount(), transaction.getCustomerId());
            features.setSharedCounterpartyAccountCustomerCount(sharedCount == null ? 0 : sharedCount);
        }
        Integer alertCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_alert
                WHERE customer_id = ?
                  AND related_transaction_ids LIKE ?
                """, Integer.class, transaction.getCustomerId(), "%" + transaction.getId() + "%");
        features.setRelatedAlertCount(alertCount == null ? 0 : alertCount);
    }

    private void addIdentityFactors(List<AiRiskFactorVO> factors, Customer customer) {
        if (Boolean.TRUE.equals(customer.getIsSanctioned())) {
            factors.add(factor("IDENTITY_SANCTION", "制裁名单标记", "身份风险", 32,
                    "客户已被标记为制裁名单命中。", "立即进入增强尽调和高优先级复核。"));
        }
        if (Boolean.TRUE.equals(customer.getIsPep())) {
            factors.add(factor("IDENTITY_PEP", "PEP敏感身份", "身份风险", 18,
                    "客户存在PEP或敏感身份标记。", "保持持续监控并核查资金来源。"));
        }
        if (isHighRiskOccupation(customer.getOccupation()) || isHighRiskOccupation(customer.getBusinessScope())) {
            factors.add(factor("IDENTITY_HIGH_RISK_OCCUPATION", "高风险职业/行业", "身份风险", 10,
                    "客户职业或经营范围包含高风险关键词。", "补充职业/经营背景和资金来源材料。"));
        }
    }

    private void addKycFactor(List<AiRiskFactorVO> factors, AiRiskFeatureSummaryVO features) {
        if (features.getKycCompleteness() < 60) {
            factors.add(factor("KYC_INCOMPLETE", "KYC资料缺口", "客户尽调", 12,
                    "客户资料完整度为 " + features.getKycCompleteness() + "%。", "先补齐身份、联系方式、地址和职业/经营资料。"));
        } else if (features.getKycCompleteness() < 85) {
            factors.add(factor("KYC_PARTIAL", "KYC资料需完善", "客户尽调", 6,
                    "客户资料完整度为 " + features.getKycCompleteness() + "%。", "建议在下一次复核时补齐缺失字段。"));
        }
    }

    private void addTransactionBehaviorFactors(List<AiRiskFactorVO> factors, AiRiskFeatureSummaryVO features) {
        if (features.getHighAmountTransactionCount90d() > 0) {
            factors.add(factor("TXN_HIGH_AMOUNT", "大额交易活跃", "交易行为",
                    Math.min(18, 8 + features.getHighAmountTransactionCount90d() * 2),
                    "近90天大额交易 " + features.getHighAmountTransactionCount90d() + " 笔，最大单笔 " + money(features.getMaxAmount90d()) + "。",
                    "核查交易目的、保单场景和资金来源。"));
        }
        if (features.getCashTransactionCount90d() > 0) {
            factors.add(factor("TXN_CASH", "现金交易关注", "交易行为",
                    Math.min(12, 5 + features.getCashTransactionCount90d()),
                    "近90天现金交易 " + features.getCashTransactionCount90d() + " 笔。",
                    "关注现金缴费、退保或理赔链路是否合理。"));
        }
        if (features.getCrossBorderTransactionCount90d() > 0) {
            factors.add(factor("TXN_CROSS_BORDER", "跨境交易", "交易行为",
                    Math.min(14, 7 + features.getCrossBorderTransactionCount90d() * 2),
                    "近90天跨境交易 " + features.getCrossBorderTransactionCount90d() + " 笔。",
                    "结合税收居民身份、交易对手国家和材料真实性复核。"));
        }
        if (features.getDistinctCounterpartyCount90d() >= 8) {
            factors.add(factor("TXN_COUNTERPARTY_DENSITY", "交易对手密集", "关系复杂度", 10,
                    "近90天不同交易对手 " + features.getDistinctCounterpartyCount90d() + " 个。",
                    "建议使用交易关系图谱排查集中转移或分散过账。"));
        }
    }

    private void addDispositionFactors(List<AiRiskFactorVO> factors, AiRiskFeatureSummaryVO features) {
        if (features.getHighRiskAlertCount() > 0) {
            factors.add(factor("ALERT_HIGH_RISK", "高风险预警", "处置链路",
                    Math.min(18, 8 + features.getHighRiskAlertCount() * 3),
                    "客户存在高风险/极高风险预警 " + features.getHighRiskAlertCount() + " 条。",
                    "优先复核命中规则和关联交易。"));
        }
        if (features.getConfirmedSuspiciousAlertCount() > 0) {
            factors.add(factor("ALERT_CONFIRMED", "已确认可疑", "处置链路", 16,
                    "客户已有确认可疑预警 " + features.getConfirmedSuspiciousAlertCount() + " 条。",
                    "应核查是否已升级案件或形成STR。"));
        }
        if (features.getCaseCount() > 0) {
            factors.add(factor("CASE_HISTORY", "案件历史", "处置链路", Math.min(14, 8 + features.getCaseCount() * 2),
                    "客户关联案件 " + features.getCaseCount() + " 个。", "复核案件结论和当前交易是否存在延续风险。"));
        }
        if (features.getStrReportCount() > 0) {
            factors.add(factor("STR_HISTORY", "STR报送历史", "处置链路", 18,
                    "客户已关联STR报告 " + features.getStrReportCount() + " 份。", "进入持续监测并保留完整证据链。"));
        }
        if (features.getWatchlistHitCount() > 0) {
            factors.add(factor("WATCHLIST_HIT", "名单筛查命中", "名单筛查", 24,
                    "客户存在名单筛查高分命中 " + features.getWatchlistHitCount() + " 条。", "立即核验命中名单项、证件和别名。"));
        }
    }

    private void addRelationshipFactors(List<AiRiskFactorVO> factors, AiRiskFeatureSummaryVO features) {
        if (features.getControllingOwnerCount() > 0) {
            factors.add(factor("OWNER_CONTROL", "受益控制集中", "关系复杂度",
                    Math.min(10, 5 + features.getControllingOwnerCount() * 2),
                    "存在持股或控制比例较高的受益所有人 " + features.getControllingOwnerCount() + " 位。",
                    "建议核查实际控制关系和受益安排。"));
        }
        if (features.getHighRiskProductCount() > 0) {
            factors.add(factor("PRODUCT_HIGH_RISK", "高风险产品关联", "产品风险", 8,
                    "客户关联高风险产品 " + features.getHighRiskProductCount() + " 个。", "复核产品结构、现金价值和退保灵活性。"));
        }
    }

    private void addTransactionAmountFactor(List<AiRiskFactorVO> factors, Transaction transaction, AiRiskFeatureSummaryVO features) {
        BigDecimal amount = transaction.getAmount() == null ? BigDecimal.ZERO : transaction.getAmount();
        if (amount.compareTo(VERY_HIGH_AMOUNT_THRESHOLD) >= 0) {
            factors.add(factor("TXN_VERY_HIGH_AMOUNT", "超高金额交易", "交易行为", 24,
                    "本笔交易金额 " + money(amount) + "，达到超高金额关注区间。", "优先核查资金来源、保单目的和交易对手。"));
        } else if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) >= 0) {
            factors.add(factor("TXN_HIGH_AMOUNT_SINGLE", "大额交易", "交易行为", 14,
                    "本笔交易金额 " + money(amount) + "。", "结合客户历史交易和产品场景复核。"));
        }
        if (features.getAmountToAverageRatio().compareTo(BigDecimal.valueOf(3)) >= 0) {
            factors.add(factor("TXN_AMOUNT_DEVIATION", "金额偏离历史均值", "交易行为", 14,
                    "本笔金额约为历史均值的 " + features.getAmountToAverageRatio() + " 倍。", "核查是否存在突发异常缴费、退保或理赔。"));
        }
    }

    private void addTransactionAttributeFactors(List<AiRiskFactorVO> factors, Transaction transaction, AiRiskFeatureSummaryVO features) {
        if ("CASH".equalsIgnoreCase(transaction.getPaymentMethod())) {
            factors.add(factor("TXN_CASH_SINGLE", "现金支付", "交易行为", 9,
                    "本笔交易使用现金支付方式。", "核实现金来源和经办材料。"));
        }
        if (Boolean.TRUE.equals(transaction.getIsCrossBorder())) {
            factors.add(factor("TXN_CROSS_BORDER_SINGLE", "跨境交易", "交易行为", 12,
                    "本笔交易带有跨境交易标记。", "核查交易对手国家、税收居民身份和资金证明。"));
        }
        if (features.getSharedCounterpartyAccountCustomerCount() > 0) {
            factors.add(factor("TXN_SHARED_ACCOUNT", "交易对手账户复用", "关系复杂度", 12,
                    "该交易对手账户还关联 " + features.getSharedCounterpartyAccountCustomerCount() + " 个其他客户。",
                    "建议使用共同账户图谱排查代缴、过账或团伙关系。"));
        }
        double anomalyScore = transactionAnomalyDetector.predict(transaction);
        if (anomalyScore >= 0.7d) {
            factors.add(factor("TXN_ISOLATION_FOREST", "机器学习异常分", "模型评分",
                    anomalyScore >= 0.85d ? 18 : 12,
                    "Isolation Forest异常分为 " + String.format(Locale.ROOT, "%.2f", anomalyScore) + "。",
                    "建议结合模型特征和交易证据复核。"));
        }
    }

    private void addCustomerContextFactors(List<AiRiskFactorVO> factors, Customer customer) {
        int baseScore = customer.getRiskScore() == null ? 0 : customer.getRiskScore();
        if ("HIGH".equals(customer.getRiskLevel()) || "CRITICAL".equals(customer.getRiskLevel()) || baseScore >= 70) {
            factors.add(factor("CUSTOMER_HIGH_RISK_CONTEXT", "高风险客户背景", "客户背景", 16,
                    "客户当前风险等级为 " + customer.getRiskLevel() + "，评分 " + baseScore + "。", "本次交易或预警应按高风险客户标准处置。"));
        } else if ("MEDIUM".equals(customer.getRiskLevel()) || baseScore >= 40) {
            factors.add(factor("CUSTOMER_MEDIUM_RISK_CONTEXT", "中风险客户背景", "客户背景", 8,
                    "客户当前风险等级为 " + customer.getRiskLevel() + "，评分 " + baseScore + "。", "结合交易目的和历史行为复核。"));
        }
        addIdentityFactors(factors, customer);
    }

    private void addRelatedAlertFactor(List<AiRiskFactorVO> factors, AiRiskFeatureSummaryVO features) {
        if (features.getRelatedAlertCount() > 0) {
            factors.add(factor("TXN_RELATED_ALERT", "已触发预警", "处置链路", 18,
                    "该交易已关联预警 " + features.getRelatedAlertCount() + " 条。", "进入预警详情查看规则命中和处置状态。"));
        }
    }

    private void addAlertNativeFactor(List<AiRiskFactorVO> factors, Alert alert) {
        int score = alert.getRiskScore() == null ? 0 : alert.getRiskScore();
        if ("CRITICAL".equals(alert.getRiskLevel()) || score >= 90) {
            factors.add(factor("ALERT_CRITICAL_NATIVE", "极高风险预警", "预警自身", 28,
                    "预警风险等级为 " + alert.getRiskLevel() + "，评分 " + score + "。", "建议立即升级调查。"));
        } else if ("HIGH".equals(alert.getRiskLevel()) || score >= 70) {
            factors.add(factor("ALERT_HIGH_NATIVE", "高风险预警", "预警自身", 20,
                    "预警风险等级为 " + alert.getRiskLevel() + "，评分 " + score + "。", "优先处理并核查关联交易。"));
        } else if ("MEDIUM".equals(alert.getRiskLevel()) || score >= 40) {
            factors.add(factor("ALERT_MEDIUM_NATIVE", "中风险预警", "预警自身", 10,
                    "预警风险等级为 " + alert.getRiskLevel() + "，评分 " + score + "。", "结合客户画像复核合理性。"));
        }
        if (StringUtils.hasText(alert.getSourceRuleCodes())) {
            factors.add(factor("ALERT_RULE_HIT", "规则命中", "规则证据", 8,
                    "命中规则：" + alert.getSourceRuleCodes() + "。", "查看命中规则详情和阈值配置。"));
        }
    }

    private void addAlertStatusFactor(List<AiRiskFactorVO> factors, Alert alert) {
        if ("CONFIRMED_SUSPICIOUS".equals(alert.getProcessResult())) {
            factors.add(factor("ALERT_CONFIRMED_RESULT", "处理结论确认可疑", "处置链路", 20,
                    "该预警处理结论为确认可疑。", "建议检查案件升级和STR生成状态。"));
        } else if ("ESCALATED".equals(alert.getStatus()) || "ESCALATED".equals(alert.getProcessResult())) {
            factors.add(factor("ALERT_ESCALATED", "已升级处理", "处置链路", 14,
                    "该预警已进入升级处置状态。", "继续跟踪案件和审批节点。"));
        }
    }

    private AiRiskScoreVO buildResult(String subjectType, Long subjectId, String subjectName,
                                      AiRiskFeatureSummaryVO features, List<AiRiskFactorVO> factors) {
        AiRiskScoreVO result = new AiRiskScoreVO();
        result.setSubjectType(subjectType);
        result.setSubjectId(subjectId);
        result.setSubjectName(subjectName);
        result.setFeatureSummary(features);
        factors.sort(Comparator.comparingInt(AiRiskFactorVO::getContribution).reversed());
        result.setFactors(factors);
        int score = clamp(factors.stream().mapToInt(AiRiskFactorVO::getContribution).sum());
        result.setScore(score);
        result.setRiskLevel(toRiskLevel(score));
        result.setScoredAt(LocalDateTime.now());
        return result;
    }

    private AmlModel resolveServingModel() {
        return amlModelMapper.selectOne(new LambdaQueryWrapper<AmlModel>()
                .eq(AmlModel::getModelCode, MODEL_CODE)
                .last("LIMIT 1"));
    }

    private void applyModelContext(AiRiskScoreVO result, AmlModel model) {
        if (model == null) {
            result.setModelCode(MODEL_CODE);
            result.setModelName(MODEL_NAME);
            result.setModelVersion(MODEL_VERSION);
            return;
        }
        result.setModelId(model.getId());
        result.setModelCode(model.getModelCode());
        result.setModelName(model.getModelName());
        result.setModelVersion(StringUtils.hasText(model.getVersion()) ? model.getVersion() : MODEL_VERSION);
    }

    private void applyStatusModelContext(AiRiskModelStatusVO status, AmlModel model) {
        if (model == null) {
            status.setModelCode(MODEL_CODE);
            status.setModelName(MODEL_NAME);
            status.setModelVersion(MODEL_VERSION);
            return;
        }
        status.setModelId(model.getId());
        status.setModelCode(model.getModelCode());
        status.setModelName(model.getModelName());
        status.setModelVersion(StringUtils.hasText(model.getVersion()) ? model.getVersion() : MODEL_VERSION);
        status.setLifecycleStatus(model.getLifecycleStatus());
        status.setMonitorStatus(model.getMonitorStatus());
        status.setStatus("MONITORING".equals(model.getLifecycleStatus()) || "DEPLOYED".equals(model.getLifecycleStatus())
                ? "SERVING"
                : model.getLifecycleStatus());
        status.setDescription(StringUtils.hasText(model.getDescription()) ? model.getDescription() : status.getDescription());
    }

    private AiRiskScoreVO persistScoreRecord(AiRiskScoreVO result, Long customerId, Long transactionId, Long alertId, AmlModel model) {
        AiRiskScoreRecord record = new AiRiskScoreRecord();
        record.setScoreNo(generateScoreNo());
        record.setSubjectType(result.getSubjectType());
        record.setSubjectId(result.getSubjectId());
        record.setSubjectName(result.getSubjectName());
        record.setCustomerId(customerId);
        record.setTransactionId(transactionId);
        record.setAlertId(alertId);
        record.setModelId(model == null ? null : model.getId());
        record.setModelCode(result.getModelCode());
        record.setModelName(result.getModelName());
        record.setModelVersion(result.getModelVersion());
        record.setScore(result.getScore());
        record.setRiskLevel(result.getRiskLevel());
        record.setConfidence(result.getConfidence());
        record.setFactorSummary(buildFactorSummary(result));
        record.setFeatureSnapshotJson(toJson(result.getFeatureSummary()));
        record.setFactorSnapshotJson(toJson(result.getFactors()));
        record.setEvidenceSnapshotJson(toJson(result.getEvidence()));
        record.setRecommendationJson(toJson(result.getRecommendations()));
        record.setScoredAt(result.getScoredAt());
        scoreRecordMapper.insert(record);

        result.setRecordId(record.getId());
        result.setScoreNo(record.getScoreNo());
        return result;
    }

    private String generateScoreNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "AIRISK" + timestamp + suffix;
    }

    private String buildFactorSummary(AiRiskScoreVO result) {
        if (result.getFactors() == null || result.getFactors().isEmpty()) {
            return "未识别明显高贡献风险因子";
        }
        return result.getFactors().stream()
                .limit(5)
                .map(factor -> factor.getFactorName() + "(+" + factor.getContribution() + ")")
                .reduce((left, right) -> left + "；" + right)
                .orElse("未识别明显高贡献风险因子");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("AI风险评分快照序列化失败", ex);
            return "{}";
        }
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

    private AiRiskReviewPoolItemVO toReviewPoolItem(AiRiskScoreRecord record) {
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

    private String normalizeSubjectType(String subjectType) {
        String normalized = StringUtils.hasText(subjectType) ? subjectType.trim().toUpperCase(Locale.ROOT) : "";
        if (!List.of("CUSTOMER", "TRANSACTION", "ALERT").contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的AI评分主体类型：" + subjectType);
        }
        return normalized;
    }

    private void addCustomerEvidenceAndRecommendations(AiRiskScoreVO result, Customer customer, AiRiskFeatureSummaryVO features) {
        result.getEvidence().add("客户当前系统风险等级：" + nullToDash(customer.getRiskLevel()) + "，原始风险评分：" + (customer.getRiskScore() == null ? 0 : customer.getRiskScore()) + "。");
        result.getEvidence().add("近90天交易 " + features.getTransactionCount90d() + " 笔，合计 " + money(features.getTotalAmount90d()) + "，交易对手 " + features.getDistinctCounterpartyCount90d() + " 个。");
        result.getEvidence().add("预警 " + features.getActiveAlertCount() + " 条活跃，案件 " + features.getCaseCount() + " 个，STR " + features.getStrReportCount() + " 份。");
        addCommonRecommendations(result);
    }

    private void addTransactionEvidenceAndRecommendations(AiRiskScoreVO result, Transaction transaction, Customer customer, AiRiskFeatureSummaryVO features) {
        result.getEvidence().add("交易金额：" + money(transaction.getAmount()) + "，交易类型：" + nullToDash(transaction.getTransactionType()) + "，支付方式：" + nullToDash(transaction.getPaymentMethod()) + "。");
        result.getEvidence().add("客户当前风险等级：" + nullToDash(customer.getRiskLevel()) + "，近90天历史交易 " + features.getTransactionCount90d() + " 笔。");
        if (features.getAmountToAverageRatio().compareTo(BigDecimal.ZERO) > 0) {
            result.getEvidence().add("本笔金额约为客户近90天历史均值的 " + features.getAmountToAverageRatio() + " 倍。");
        }
        addCommonRecommendations(result);
    }

    private void addAlertEvidenceAndRecommendations(AiRiskScoreVO result, Alert alert, Customer customer, AiRiskFeatureSummaryVO features) {
        result.getEvidence().add("预警类型：" + nullToDash(alert.getAlertType()) + "，风险等级：" + nullToDash(alert.getRiskLevel()) + "，预警评分：" + (alert.getRiskScore() == null ? 0 : alert.getRiskScore()) + "。");
        result.getEvidence().add("客户当前风险等级：" + nullToDash(customer.getRiskLevel()) + "，关联交易数量：" + features.getRelatedAlertCount() + "。");
        result.getEvidence().add("客户历史案件 " + features.getCaseCount() + " 个，STR " + features.getStrReportCount() + " 份。");
        addCommonRecommendations(result);
    }

    private void addCommonRecommendations(AiRiskScoreVO result) {
        if ("CRITICAL".equals(result.getRiskLevel())) {
            result.getRecommendations().add("建议立即升级为高优先级人工复核，并检查是否需要生成或补充STR。");
        } else if ("HIGH".equals(result.getRiskLevel())) {
            result.getRecommendations().add("建议进入增强尽调，核查资金来源、交易目的和关系图谱证据。");
        } else if ("MEDIUM".equals(result.getRiskLevel())) {
            result.getRecommendations().add("建议持续关注，并在下一轮复核中补充缺失资料或交易说明。");
        } else {
            result.getRecommendations().add("当前AI评分未见明显高风险信号，保持常规监测。");
        }
        result.getRecommendations().add("AI评分作为辅助研判结果，最终处置应由合规人员结合证据链确认。");
    }

    private int calculateCustomerConfidence(Customer customer, AiRiskFeatureSummaryVO features) {
        int confidence = 45;
        confidence += Math.min(20, features.getTransactionCount90d() * 2);
        confidence += Math.min(12, features.getActiveAlertCount() * 3);
        confidence += features.getKycCompleteness() / 5;
        if (Boolean.TRUE.equals(customer.getIsPep()) || Boolean.TRUE.equals(customer.getIsSanctioned())) {
            confidence += 8;
        }
        return clamp(confidence);
    }

    private int calculateTransactionConfidence(Transaction transaction, AiRiskFeatureSummaryVO features) {
        int confidence = 55;
        confidence += Math.min(20, features.getTransactionCount90d() * 2);
        if (transaction.getAmount() != null) confidence += 8;
        if (StringUtils.hasText(transaction.getCounterpartyName()) || StringUtils.hasText(transaction.getCounterpartyAccount())) confidence += 8;
        if (features.getRelatedAlertCount() > 0) confidence += 8;
        return clamp(confidence);
    }

    private int calculateAlertConfidence(Alert alert, AiRiskFeatureSummaryVO features) {
        int confidence = 65;
        if (alert.getRiskScore() != null) confidence += 8;
        if (StringUtils.hasText(alert.getSourceRuleCodes())) confidence += 8;
        if (features.getRelatedAlertCount() > 0) confidence += 8;
        if (features.getCaseCount() > 0 || features.getStrReportCount() > 0) confidence += 8;
        return clamp(confidence);
    }

    private int countForCustomer(String tableName, Long customerId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE customer_id = ?", Integer.class, customerId);
        return count == null ? 0 : count;
    }

    private int countWatchlistHits(Long customerId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_screening_result
                WHERE customer_id = ?
                  AND match_score >= ?
                  AND COALESCE(review_result, '') <> 'FALSE_POSITIVE'
                """, Integer.class, customerId, WATCHLIST_HIT_THRESHOLD);
        return count == null ? 0 : count;
    }

    private int countControllingOwners(Long customerId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_customer_beneficial_owner
                WHERE customer_id = ?
                  AND COALESCE(ownership_percentage, 0) >= 25
                  AND status = 'ACTIVE'
                """, Integer.class, customerId);
        return count == null ? 0 : count;
    }

    private int countRelatedTransactions(String relatedTransactionIds) {
        if (!StringUtils.hasText(relatedTransactionIds)) {
            return 0;
        }
        return (int) java.util.Arrays.stream(relatedTransactionIds.split("[,;\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .count();
    }

    private int calculateKycCompleteness(Customer customer) {
        List<Object> fields = Arrays.asList(
                customer.getName(),
                customer.getCustomerType(),
                customer.getIdType(),
                customer.getIdNumber(),
                customer.getNationality(),
                customer.getPhone(),
                customer.getEmail(),
                firstNonBlank(customer.getAddress(), customer.getResidenceAddress()),
                firstNonBlank(customer.getOccupation(), customer.getEnterpriseType(), customer.getBusinessScope())
        );
        long filled = fields.stream().filter(value -> value != null && StringUtils.hasText(String.valueOf(value))).count();
        int score = (int) Math.round(filled * 100.0 / fields.size());
        if ("COMPLETE".equals(customer.getKycStatus()) || "COMPLETED".equals(customer.getKycStatus())) {
            score += 8;
        }
        return clamp(score);
    }

    private AiRiskFactorVO factor(String code, String name, String category, int contribution,
                                  String evidence, String suggestion) {
        return AiRiskFactorVO.builder()
                .factorCode(code)
                .factorName(name)
                .category(category)
                .contribution(Math.max(0, contribution))
                .weight(BigDecimal.valueOf(contribution).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .evidence(evidence)
                .suggestion(suggestion)
                .build();
    }

    private String toRiskLevel(int score) {
        if (score >= 85) return "CRITICAL";
        if (score >= 65) return "HIGH";
        if (score >= 35) return "MEDIUM";
        return "LOW";
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private Integer intValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private BigDecimal decimalValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return "CNY " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
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

    private boolean isHighRiskOccupation(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String[] keywords = {"博彩", "典当", "贵金属", "珠宝", "地下钱庄", "换汇", "虚拟货币", "跨境贸易"};
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
