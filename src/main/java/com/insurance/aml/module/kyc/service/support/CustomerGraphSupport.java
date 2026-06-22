package com.insurance.aml.module.kyc.service.support;

import com.insurance.aml.module.kyc.model.dto.CustomerRelationshipGraphVO;
import com.insurance.aml.module.kyc.model.dto.CustomerVO;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 客户关系图谱节点、关系和字段转换辅助逻辑。
 */
public final class CustomerGraphSupport {

    private CustomerGraphSupport() {
    }

    public static void addNode(Map<String, CustomerRelationshipGraphVO.Node> nodes, CustomerRelationshipGraphVO.Node node) {
        nodes.putIfAbsent(node.getId(), node);
    }

    public static CustomerRelationshipGraphVO.Link link(String source, String target, String label, BigDecimal value, String riskLevel) {
        CustomerRelationshipGraphVO.Link link = new CustomerRelationshipGraphVO.Link();
        link.setSource(source);
        link.setTarget(target);
        link.setLabel(label);
        link.setValue(value);
        link.setRiskLevel(riskLevel);
        return link;
    }

    public static NodeBuilder node(String id, String label, String type, String category) {
        return new NodeBuilder(id, firstNonBlank(label, category), type, category);
    }

    public static Map<String, Object> copyDetail(Map<String, Object> row) {
        Map<String, Object> detail = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            if (value != null) {
                detail.put(key, value);
            }
        });
        return detail;
    }

    public static String stringValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    public static Integer intValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static BigDecimal decimalValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean booleanValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    public static List<String> splitIds(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    public static String normalizeId(String value) {
        return value == null ? "" : value.trim().replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-");
    }

    public static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    public static String firstNonBlank(String... values) {
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

    public static void fillGraphSummary(CustomerRelationshipGraphVO graph,
                                        CustomerVO customerVO,
                                        List<Map<String, Object>> policies,
                                        List<Map<String, Object>> transactions,
                                        List<Map<String, Object>> alerts,
                                        List<Map<String, Object>> cases,
                                        List<Map<String, Object>> strReports,
                                        List<Map<String, Object>> screeningResults) {
        CustomerRelationshipGraphVO.Summary summary = graph.getSummary();
        summary.setNodeCount(graph.getNodes().size());
        summary.setLinkCount(graph.getLinks().size());
        summary.setBeneficialOwnerCount(customerVO.getBeneficialOwners() == null ? 0 : customerVO.getBeneficialOwners().size());
        summary.setPolicyCount(policies.size());
        summary.setProductCount((int) policies.stream()
                .map(row -> stringValue(row, "productId"))
                .filter(StringUtils::hasText)
                .distinct()
                .count());
        summary.setTransactionCount(transactions.size());
        summary.setAlertCount(alerts.size());
        summary.setCaseCount(cases.size());
        summary.setStrReportCount(strReports.size());
        summary.setWatchlistHitCount(screeningResults.size());
        summary.setRiskSignalCount(alerts.size() + screeningResults.size()
                + (Boolean.TRUE.equals(customerVO.getIsPep()) ? 1 : 0)
                + (Boolean.TRUE.equals(customerVO.getIsSanctioned()) ? 1 : 0));
        summary.setTotalTransactionAmount(transactions.stream()
                .map(row -> decimalValue(row, "amount"))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public static List<String> buildGraphInsights(CustomerVO customerVO, CustomerRelationshipGraphVO.Summary summary) {
        List<String> insights = new ArrayList<>();
        insights.add("已串联客户、受益关系、保单产品、交易行为与处置链路，共 " + summary.getNodeCount() + " 个节点、" + summary.getLinkCount() + " 条关系。");
        if (summary.getWatchlistHitCount() > 0 || Boolean.TRUE.equals(customerVO.getIsPep()) || Boolean.TRUE.equals(customerVO.getIsSanctioned())) {
            insights.add("名单与敏感身份是主要风险来源之一，建议重点核对筛查复核结论和身份背景资料。");
        }
        if (summary.getTransactionCount() > 0) {
            insights.add("近 " + summary.getTransactionCount() + " 笔交易金额合计 " + summary.getTotalTransactionAmount().stripTrailingZeros().toPlainString() + "，可结合交易对手与保单节点识别资金路径。");
        }
        if (summary.getAlertCount() > 0 || summary.getCaseCount() > 0 || summary.getStrReportCount() > 0) {
            insights.add("处置链路已覆盖预警、案件与 STR 报告，可一眼追踪从风险触发到调查报送的闭环状态。");
        }
        if (summary.getPolicyCount() == 0 && summary.getTransactionCount() == 0 && summary.getAlertCount() == 0) {
            insights.add("当前客户暂无保单、交易或处置数据，图谱将随业务数据进入后自动扩展。");
        }
        return insights;
    }

    public static boolean isHighRiskOccupation(String occupation) {
        if (!StringUtils.hasText(occupation)) {
            return false;
        }
        String[] highRiskKeywords = {"博彩", "典当", "贵金属", "珠宝", "地下钱庄", "换汇"};
        for (String keyword : highRiskKeywords) {
            if (occupation.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static class NodeBuilder {
        private final CustomerRelationshipGraphVO.Node node = new CustomerRelationshipGraphVO.Node();

        NodeBuilder(String id, String label, String type, String category) {
            node.setId(id);
            node.setLabel(label);
            node.setType(type);
            node.setCategory(category);
        }

        public NodeBuilder status(String status) {
            node.setStatus(status);
            return this;
        }

        public NodeBuilder riskLevel(String riskLevel) {
            node.setRiskLevel(riskLevel);
            return this;
        }

        public NodeBuilder riskScore(Integer riskScore) {
            node.setRiskScore(riskScore);
            return this;
        }

        public NodeBuilder amount(BigDecimal amount) {
            node.setAmount(amount);
            return this;
        }

        public NodeBuilder detail(Map<String, Object> detail) {
            node.setDetail(detail == null ? new LinkedHashMap<>() : new LinkedHashMap<>(detail));
            return this;
        }

        public CustomerRelationshipGraphVO.Node build() {
            return node;
        }
    }
}
