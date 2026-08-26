package com.insurance.aml.module.reporting.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("监管XML校验器测试")
class RegulatoryXmlValidatorTest {
    private final RegulatoryXmlValidator validator = new RegulatoryXmlValidator();

    @Test
    @DisplayName("完整大额交易报文通过最小业务校验")
    void validLargeTransactionXmlPasses() {
        String xml = """
                <LargeTransactionReport><Header><ReportNo>R1</ReportNo><ReportDate>2026-08-26</ReportDate>
                <InstitutionCode>I1</InstitutionCode></Header><CustomerId>C1</CustomerId>
                <TransactionId>T1</TransactionId><Amount>100.00</Amount><Currency>CNY</Currency></LargeTransactionReport>
                """;
        assertTrue(validator.validate("LARGE_TXN", xml).valid());
    }

    @Test
    @DisplayName("DOCTYPE外部实体结构被拒绝")
    void doctypeIsRejected() {
        String xml = "<!DOCTYPE x [<!ENTITY ext SYSTEM \"file:///etc/passwd\">]><x>&ext;</x>";
        assertFalse(validator.validate("LARGE_TXN", xml).valid());
    }

    @Test
    @DisplayName("缺少必填字段返回可定位错误")
    void missingRequiredFieldIsReported() {
        RegulatoryValidationResult result = validator.validate("SUSPICIOUS",
                "<SuspiciousTransactionReport><ReportNo>R2</ReportNo></SuspiciousTransactionReport>");
        assertFalse(result.valid());
        assertTrue(result.summary().contains("InstitutionCode"));
        assertTrue(result.summary().contains("Content"));
    }
}
