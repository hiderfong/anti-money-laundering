package com.insurance.aml.module.ai.service.support;

import com.insurance.aml.module.ai.model.dto.AiRiskFactorVO;
import com.insurance.aml.module.ai.model.dto.AiRiskFeatureSummaryVO;
import com.insurance.aml.module.ai.model.dto.AiRiskScoreVO;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.service.TransactionAnomalyDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * AI风险评分因子评估器。
 *
 * <p>承载可解释打分的全部规则化因子目录、置信度计算与证据/建议生成，
 * 是原服务中体量最大的"业务规则噪声"，独立后便于审阅与单测。</p>
 */
@Component
@RequiredArgsConstructor
public class AiRiskFactorEvaluator {

    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = BigDecimal.valueOf(50_000);
    private static final BigDecimal VERY_HIGH_AMOUNT_THRESHOLD = BigDecimal.valueOf(200_000);

    private final TransactionAnomalyDetector transactionAnomalyDetector;

    // ---- 客户维度因子 ----

    public void addIdentityFactors(List<AiRiskFactorVO> factors, Customer customer) {
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

    public void addKycFactor(List<AiRiskFactorVO> factors, AiRiskFeatureSummaryVO features) {
        if (features.getKycCompleteness() < 60) {
            factors.add(factor("KYC_INCOMPLETE", "KYC资料缺口", "客户尽调", 12,
                    "客户资料完整度为 " + features.getKycCompleteness() + "%。", "先补齐身份、联系方式、地址和职业/经营资料。"));
        } else if (features.getKycCompleteness() < 85) {
            factors.add(factor("KYC_PARTIAL", "KYC资料需完善", "客户尽调", 6,
                    "客户资料完整度为 " + features.getKycCompleteness() + "%。", "建议在下一次复核时补齐缺失字段。"));
        }
    }

    public void addTransactionBehaviorFactors(List<AiRiskFactorVO> factors, AiRiskFeatureSummaryVO features) {
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

    public void addDispositionFactors(List<AiRiskFactorVO> factors, AiRiskFeatureSummaryVO features) {
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

    public void addRelationshipFactors(List<AiRiskFactorVO> factors, AiRiskFeatureSummaryVO features) {
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

    // ---- 交易维度因子 ----

    public void addTransactionAmountFactor(List<AiRiskFactorVO> factors, Transaction transaction, AiRiskFeatureSummaryVO features) {
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

    public void addTransactionAttributeFactors(List<AiRiskFactorVO> factors, Transaction transaction, AiRiskFeatureSummaryVO features) {
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

    public void addCustomerContextFactors(List<AiRiskFactorVO> factors, Customer customer) {
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

    public void addRelatedAlertFactor(List<AiRiskFactorVO> factors, AiRiskFeatureSummaryVO features) {
        if (features.getRelatedAlertCount() > 0) {
            factors.add(factor("TXN_RELATED_ALERT", "已触发预警", "处置链路", 18,
                    "该交易已关联预警 " + features.getRelatedAlertCount() + " 条。", "进入预警详情查看规则命中和处置状态。"));
        }
    }

    // ---- 预警维度因子 ----

    public void addAlertNativeFactor(List<AiRiskFactorVO> factors, Alert alert) {
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

    public void addAlertStatusFactor(List<AiRiskFactorVO> factors, Alert alert) {
        if ("CONFIRMED_SUSPICIOUS".equals(alert.getProcessResult())) {
            factors.add(factor("ALERT_CONFIRMED_RESULT", "处理结论确认可疑", "处置链路", 20,
                    "该预警处理结论为确认可疑。", "建议检查案件升级和STR生成状态。"));
        } else if ("ESCALATED".equals(alert.getStatus()) || "ESCALATED".equals(alert.getProcessResult())) {
            factors.add(factor("ALERT_ESCALATED", "已升级处理", "处置链路", 14,
                    "该预警已进入升级处置状态。", "继续跟踪案件和审批节点。"));
        }
    }

    // ---- 置信度 ----

    public int calculateCustomerConfidence(Customer customer, AiRiskFeatureSummaryVO features) {
        int confidence = 45;
        confidence += Math.min(20, features.getTransactionCount90d() * 2);
        confidence += Math.min(12, features.getActiveAlertCount() * 3);
        confidence += features.getKycCompleteness() / 5;
        if (Boolean.TRUE.equals(customer.getIsPep()) || Boolean.TRUE.equals(customer.getIsSanctioned())) {
            confidence += 8;
        }
        return clamp(confidence);
    }

    public int calculateTransactionConfidence(Transaction transaction, AiRiskFeatureSummaryVO features) {
        int confidence = 55;
        confidence += Math.min(20, features.getTransactionCount90d() * 2);
        if (transaction.getAmount() != null) confidence += 8;
        if (StringUtils.hasText(transaction.getCounterpartyName()) || StringUtils.hasText(transaction.getCounterpartyAccount())) confidence += 8;
        if (features.getRelatedAlertCount() > 0) confidence += 8;
        return clamp(confidence);
    }

    public int calculateAlertConfidence(Alert alert, AiRiskFeatureSummaryVO features) {
        int confidence = 65;
        if (alert.getRiskScore() != null) confidence += 8;
        if (StringUtils.hasText(alert.getSourceRuleCodes())) confidence += 8;
        if (features.getRelatedAlertCount() > 0) confidence += 8;
        if (features.getCaseCount() > 0 || features.getStrReportCount() > 0) confidence += 8;
        return clamp(confidence);
    }

    // ---- 证据与建议 ----

    public void addCustomerEvidenceAndRecommendations(AiRiskScoreVO result, Customer customer, AiRiskFeatureSummaryVO features) {
        result.getEvidence().add("客户当前系统风险等级：" + nullToDash(customer.getRiskLevel()) + "，原始风险评分：" + (customer.getRiskScore() == null ? 0 : customer.getRiskScore()) + "。");
        result.getEvidence().add("近90天交易 " + features.getTransactionCount90d() + " 笔，合计 " + money(features.getTotalAmount90d()) + "，交易对手 " + features.getDistinctCounterpartyCount90d() + " 个。");
        result.getEvidence().add("预警 " + features.getActiveAlertCount() + " 条活跃，案件 " + features.getCaseCount() + " 个，STR " + features.getStrReportCount() + " 份。");
        addCommonRecommendations(result);
    }

    public void addTransactionEvidenceAndRecommendations(AiRiskScoreVO result, Transaction transaction, Customer customer, AiRiskFeatureSummaryVO features) {
        result.getEvidence().add("交易金额：" + money(transaction.getAmount()) + "，交易类型：" + nullToDash(transaction.getTransactionType()) + "，支付方式：" + nullToDash(transaction.getPaymentMethod()) + "。");
        result.getEvidence().add("客户当前风险等级：" + nullToDash(customer.getRiskLevel()) + "，近90天历史交易 " + features.getTransactionCount90d() + " 笔。");
        if (features.getAmountToAverageRatio().compareTo(BigDecimal.ZERO) > 0) {
            result.getEvidence().add("本笔金额约为客户近90天历史均值的 " + features.getAmountToAverageRatio() + " 倍。");
        }
        addCommonRecommendations(result);
    }

    public void addAlertEvidenceAndRecommendations(AiRiskScoreVO result, Alert alert, Customer customer, AiRiskFeatureSummaryVO features) {
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

    // ---- 内部工具 ----

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

    private String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return "CNY " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
