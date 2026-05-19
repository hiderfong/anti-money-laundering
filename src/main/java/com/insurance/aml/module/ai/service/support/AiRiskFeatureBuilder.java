package com.insurance.aml.module.ai.service.support;

import com.insurance.aml.module.ai.model.dto.AiRiskFeatureSummaryVO;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * AI风险评分特征聚合器。
 *
 * <p>负责把客户/交易维度的数据库聚合结果装配为可解释特征快照，
 * 与因子打分、记录复核解耦。行为与原 {@code AiRiskScoringServiceImpl} 保持一致。</p>
 */
@Component
@RequiredArgsConstructor
public class AiRiskFeatureBuilder {

    private static final int WINDOW_DAYS = 90;
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = BigDecimal.valueOf(50_000);
    private static final BigDecimal WATCHLIST_HIT_THRESHOLD = BigDecimal.valueOf(80);

    private final JdbcTemplate jdbcTemplate;

    public AiRiskFeatureSummaryVO buildCustomerFeatures(Customer customer) {
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

    public void enrichTransactionFeatures(AiRiskFeatureSummaryVO features, Transaction transaction) {
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

    public int countRelatedTransactions(String relatedTransactionIds) {
        if (!StringUtils.hasText(relatedTransactionIds)) {
            return 0;
        }
        return (int) Arrays.stream(relatedTransactionIds.split("[,;\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .count();
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
        return Math.max(0, Math.min(100, score));
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
}
