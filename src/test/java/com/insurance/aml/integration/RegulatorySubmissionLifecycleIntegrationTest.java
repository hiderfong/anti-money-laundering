package com.insurance.aml.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.common.enums.ReportStatus;
import com.insurance.aml.module.integration.mapper.IntegrationConnectorMapper;
import com.insurance.aml.module.integration.model.entity.IntegrationConnector;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.reporting.mapper.LargeTxnReportMapper;
import com.insurance.aml.module.reporting.mapper.RegulatoryReceiptMapper;
import com.insurance.aml.module.reporting.mapper.RegulatorySubmissionMapper;
import com.insurance.aml.module.reporting.model.entity.LargeTxnReport;
import com.insurance.aml.module.reporting.model.entity.RegulatoryReceipt;
import com.insurance.aml.module.reporting.model.entity.RegulatorySubmission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("监管报送版本、回执与重报闭环测试")
class RegulatorySubmissionLifecycleIntegrationTest extends BaseIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CustomerMapper customerMapper;
    @Autowired private TransactionMapper transactionMapper;
    @Autowired private LargeTxnReportMapper largeTxnReportMapper;
    @Autowired private IntegrationConnectorMapper connectorMapper;
    @Autowired private RegulatorySubmissionMapper submissionMapper;
    @Autowired private RegulatoryReceiptMapper receiptMapper;

    @Test
    @WithMockUser(username = "regulatory_operator", authorities = {"report:view", "report:submit"})
    @DisplayName("已审核大额报告完成校验签名并获得即时受理回执")
    void acceptedSubmissionPersistsEvidenceChain() throws Exception {
        LargeTxnReport report = insertReviewedReport("ACCEPT");

        JsonNode submission = responseData(mockMvc.perform(post("/reporting/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"LARGE_TXN","reportId":"%s","connectorId":"99001"}
                                """.formatted(report.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.signatureAlgorithm").value("SHA-256-MOCK"))
                .andReturn());

        long submissionId = submission.path("id").asLong();
        RegulatorySubmission persisted = submissionMapper.selectById(submissionId);
        assertNotNull(persisted.getPayloadHash());
        assertNotNull(persisted.getSignatureValue());
        assertNotNull(persisted.getReceiptNo());
        assertEquals(ReportStatus.SUBMITTED.getCode(),
                largeTxnReportMapper.selectById(report.getId()).getReportStatus());

        mockMvc.perform(get("/reporting/submissions/{id}", submissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submission.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.receipts.length()").value(1));
    }

    @Test
    @WithMockUser(username = "regulatory_operator", authorities = {"report:view", "report:submit"})
    @DisplayName("退回报告填写修正说明后创建V2并重报受理")
    void rejectedSubmissionCanBeCorrectedAndResubmitted() throws Exception {
        LargeTxnReport report = insertReviewedReport("REJECT");
        IntegrationConnector rejectedConnector = insertConnector(
                "REG_REJECT_" + System.nanoTime(),
                "mock://success?receipt=REJECTED&code=CUSTOMER_ID&message=证件字段不完整");

        JsonNode rejected = responseData(mockMvc.perform(post("/reporting/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"LARGE_TXN","reportId":"%s","connectorId":"%s"}
                                """.formatted(report.getId(), rejectedConnector.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.returnCode").value("CUSTOMER_ID"))
                .andReturn());

        long rejectedId = rejected.path("id").asLong();
        JsonNode accepted = responseData(mockMvc.perform(post("/reporting/submissions/{id}/resubmit", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectorId":"99001","correctionNote":"已补全客户证件签发机关和有效期"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.parentSubmissionId").value(String.valueOf(rejectedId)))
                .andReturn());

        RegulatorySubmission versionTwo = submissionMapper.selectById(accepted.path("id").asLong());
        assertEquals("已补全客户证件签发机关和有效期", versionTwo.getCorrectionNote());
        assertEquals("RESUBMITTED", largeTxnReportMapper.selectById(report.getId()).getReportStatus());
    }

    @Test
    @WithMockUser(username = "regulatory_operator", authorities = {"report:view", "report:submit"})
    @DisplayName("待回执报送可主动查询并转为受理")
    void pendingReceiptCanBePolled() throws Exception {
        LargeTxnReport report = insertReviewedReport("PENDING");
        IntegrationConnector pendingConnector = insertConnector(
                "REG_PENDING_" + System.nanoTime(),
                "mock://success?receipt=PENDING&poll=ACCEPTED");

        JsonNode pending = responseData(mockMvc.perform(post("/reporting/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"LARGE_TXN","reportId":"%s","connectorId":"%s"}
                                """.formatted(report.getId(), pendingConnector.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.receiptStatus").value("PENDING"))
                .andReturn());

        long submissionId = pending.path("id").asLong();
        mockMvc.perform(post("/reporting/submissions/{id}/poll-receipt", submissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.receiptStatus").value("ACCEPTED"));

        long receiptCount = receiptMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RegulatoryReceipt>()
                .eq(RegulatoryReceipt::getSubmissionId, submissionId));
        assertEquals(2L, receiptCount);
    }

    private LargeTxnReport insertReviewedReport(String suffix) {
        Customer customer = new Customer();
        customer.setCustomerNo("REG-C-" + suffix + System.nanoTime());
        customer.setCustomerType("INDIVIDUAL");
        customer.setName("监管报送客户" + suffix);
        customer.setIdType("ID_CARD");
        customer.setIdNumber("11010119880101" + Math.abs((int) (System.nanoTime() % 9000)));
        customer.setNationality("CN");
        customer.setAddress("上海市浦东新区合规大道88号");
        customer.setPhone("13800138000");
        customer.setRiskLevel("HIGH");
        customer.setRiskScore(82);
        customer.setKycStatus("COMPLETE");
        customer.setStatus("ACTIVE");
        customerMapper.insert(customer);

        Transaction transaction = new Transaction();
        transaction.setTransactionNo("REG-T-" + suffix + System.nanoTime());
        transaction.setCustomerId(customer.getId());
        transaction.setTransactionType("PREMIUM");
        transaction.setAmount(new BigDecimal("680000.00"));
        transaction.setCurrency("CNY");
        transaction.setPaymentMethod("TRANSFER");
        transaction.setChannel("COUNTER");
        transaction.setCounterpartyName("华东保险资金结算中心");
        transaction.setCounterpartyAccount("6222000012345678901");
        transaction.setCounterpartyBank("中国银行上海分行");
        transaction.setIsCrossBorder(false);
        transaction.setTransactionTime(LocalDateTime.now().minusHours(1));
        transaction.setStatus("SUCCESS");
        transaction.setSourceSystem("CORE");
        transactionMapper.insert(transaction);

        LargeTxnReport report = new LargeTxnReport();
        report.setReportNo("REG-R-" + suffix + System.nanoTime());
        report.setCustomerId(customer.getId());
        report.setCustomerName(customer.getName());
        report.setTransactionId(transaction.getId());
        report.setReportDate(LocalDate.now());
        report.setTransactionTime(transaction.getTransactionTime());
        report.setTransactionType(transaction.getTransactionType());
        report.setAmount(transaction.getAmount());
        report.setCurrency(transaction.getCurrency());
        report.setPaymentMethod(transaction.getPaymentMethod());
        report.setCounterpartyInfo("{\"name\":\"华东保险资金结算中心\"}");
        report.setReportStatus(ReportStatus.REVIEWED.getCode());
        report.setReviewedBy("合规复核员");
        report.setReviewedTime(LocalDateTime.now());
        largeTxnReportMapper.insert(report);
        return report;
    }

    private IntegrationConnector insertConnector(String code, String endpoint) {
        IntegrationConnector connector = new IntegrationConnector();
        connector.setConnectorCode(code);
        connector.setConnectorName("监管报送测试连接器");
        connector.setBusinessType("REGULATORY_REPORTING");
        connector.setTransportType("MOCK");
        connector.setEndpointUrl(endpoint);
        connector.setAuthType("NONE");
        connector.setStatus("ENABLED");
        connector.setHealthStatus("HEALTHY");
        connector.setTimeoutSeconds(30);
        connector.setMaxRetries(1);
        connector.setRetryIntervalSeconds(0);
        connectorMapper.insert(connector);
        assertTrue(connector.getId() > 0);
        return connector;
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, root.path("code").asInt(), root.path("message").asText());
        return root.path("data");
    }
}
