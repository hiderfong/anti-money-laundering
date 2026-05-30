package com.insurance.aml.module.alert.service.support;

import com.insurance.aml.common.enums.AlertStatus;
import com.insurance.aml.common.enums.RiskLevel;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.alert.model.entity.AlertRuleDetail;
import com.insurance.aml.module.casemgmt.model.entity.StrReport;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 预警处置链路展示辅助逻辑。
 */
public final class AlertDispositionSupport {

    private AlertDispositionSupport() {
    }

    public static String buildRuleSummary(Alert alert, List<AlertRuleDetail> ruleDetails) {
        List<String> ruleNames = ruleDetails.stream()
                .map(item -> StringUtils.hasText(item.getRuleName()) ? item.getRuleName() : item.getRuleCode())
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (!ruleNames.isEmpty()) {
            return "命中规则：" + joinLimited(ruleNames, 3);
        }
        if (StringUtils.hasText(alert.getSourceRuleCodes())) {
            return "规则代码：" + alert.getSourceRuleCodes();
        }
        return "规则明细待补充";
    }

    public static String buildAlertMeta(Alert alert, String ruleSummary) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(alert.getAlertType())) {
            parts.add(alert.getAlertType());
        }
        if (alert.getRiskScore() != null) {
            parts.add("风险分 " + alert.getRiskScore());
        }
        if (StringUtils.hasText(ruleSummary)) {
            parts.add(ruleSummary);
        }
        return String.join(" / ", parts);
    }

    public static String resolveReviewState(Alert alert) {
        if (AlertStatus.EXCLUDED.getCode().equals(alert.getStatus())) {
            return "muted";
        }
        if (AlertStatus.CONFIRMED.getCode().equals(alert.getStatus())) {
            return "done";
        }
        if (AlertStatus.ESCALATED.getCode().equals(alert.getStatus())) {
            return "warn";
        }
        return StringUtils.hasText(alert.getProcessResult()) ? "done" : "current";
    }

    public static boolean isHighRisk(String riskLevel) {
        return RiskLevel.HIGH.getCode().equals(riskLevel) || RiskLevel.CRITICAL.getCode().equals(riskLevel);
    }

    public static boolean isSubmittedToRegulator(StrReport report) {
        return "SUBMITTED".equals(report.getReportStatus())
                || "SUBMIT_SUCCESS".equals(report.getSubmitResult())
                || "SUCCESS".equals(report.getSubmitResult())
                || report.getSubmitTime() != null && StringUtils.hasText(report.getSubmitResult());
    }

    public static LocalDateTime firstNonNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    public static String joinLimited(List<String> values, int limit) {
        List<String> filtered = values.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            return "";
        }
        String joined = filtered.stream().limit(limit).collect(Collectors.joining("、"));
        if (filtered.size() > limit) {
            joined += " 等" + filtered.size() + "项";
        }
        return joined;
    }

    public static String nullSafe(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    public static String caseStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "-";
        }
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "INVESTIGATING" -> "调查中";
            case "PENDING_APPROVAL" -> "待审批";
            case "SUBMITTED" -> "已报送";
            case "CLOSED" -> "已结案";
            default -> status;
        };
    }

    public static String reportStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "-";
        }
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "PENDING_REVIEW" -> "待审核";
            case "APPROVED" -> "已审核";
            case "REJECTED" -> "已拒绝";
            case "SUBMITTED" -> "已报送";
            default -> status;
        };
    }

    public static String alertProcessLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return switch (value) {
            case "CONFIRMED", "CONFIRMED_SUSPICIOUS" -> "确认可疑";
            case "EXCLUDED" -> "排除误报";
            case "ESCALATED" -> "升级处理";
            case "NEW" -> "新建";
            case "ASSIGNED" -> "已分配";
            case "PROCESSING" -> "处理中";
            default -> value;
        };
    }
}
