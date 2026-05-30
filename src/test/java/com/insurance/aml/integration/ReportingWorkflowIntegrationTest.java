package com.insurance.aml.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.common.enums.CaseStatus;
import com.insurance.aml.common.enums.ReportStatus;
import com.insurance.aml.common.enums.SubmitStatus;
import com.insurance.aml.module.casemgmt.mapper.CaseMapper;
import com.insurance.aml.module.casemgmt.mapper.StrReportMapper;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import com.insurance.aml.module.casemgmt.model.entity.StrReport;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.reporting.mapper.LargeTxnReportMapper;
import com.insurance.aml.module.reporting.mapper.ReportSubmitLogMapper;
import com.insurance.aml.module.reporting.model.entity.LargeTxnReport;
import com.insurance.aml.module.reporting.model.entity.ReportSubmitLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 监管报送关键流程集成测试。
 */
@DisplayName("监管报送关键流程集成测试")
class ReportingWorkflowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomerMapper customerMapper;
    @Autowired
    private TransactionMapper transactionMapper;
    @Autowired
    private CaseMapper caseMapper;
    @Autowired
    private StrReportMapper strReportMapper;
    @Autowired
    private LargeTxnReportMapper largeTxnReportMapper;
    @Autowired
    private ReportSubmitLogMapper reportSubmitLogMapper;

    @Test
    @WithMockUser(username = "1001", authorities = {"report:str", "report:submit"})
    @DisplayName("STR报告创建、审核通过并提交监管")
    void suspiciousTransactionReportFullWorkflow() throws Exception {
        Customer customer = insertCustomer("STR闭环客户");
        Case caseEntity = insertCase(customer, CaseStatus.INVESTIGATING.getCode());

        MvcResult createResult = mockMvc.perform(post("/str-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": %d,
                                  "reportType": "NORMAL",
                                  "reportContent": "客户短期内多笔大额保费缴纳后申请退保，交易模式与既有画像明显偏离。",
                                  "analysisOpinion": "建议作为可疑交易上报并持续关注资金回流链路。",
                                  "measuresTaken": "已完成客户身份复核、交易资料留存和案件调查记录归档。"
                                }
                                """.formatted(caseEntity.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reportStatus").value(ReportStatus.DRAFT.getCode()))
                .andReturn();

        long reportId = responseData(createResult).path("id").asLong();

        mockMvc.perform(post("/str-reports/{id}/submit-review", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/str-reports/{id}/review", reportId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportId": %d,
                                  "approved": true,
                                  "opinion": "调查证据充分，同意提交监管。"
                                }
                                """.formatted(reportId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(CaseStatus.PENDING_APPROVAL.getCode(),
                caseMapper.selectById(caseEntity.getId()).getCaseStatus(),
                "STR审核通过后案件应进入待审批状态");

        mockMvc.perform(post("/str-reports/{id}/submit-regulator", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/str-reports/{id}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportStatus").value(ReportStatus.SUBMITTED.getCode()))
                .andExpect(jsonPath("$.data.submitResult").value(org.hamcrest.Matchers.containsString("ACCEPTED")));

        StrReport persisted = strReportMapper.selectById(reportId);
        assertNotNull(persisted.getSubmitTime(), "STR提交监管后应记录提交时间");
        assertEquals(1L, reportSubmitLogMapper.selectCount(new LambdaQueryWrapper<ReportSubmitLog>()
                .eq(ReportSubmitLog::getReportType, "SUSPICIOUS")
                .eq(ReportSubmitLog::getReportId, reportId)
                .eq(ReportSubmitLog::getSubmitStatus, SubmitStatus.SUCCESS.getCode())));
    }

    @Test
    @WithMockUser(username = "compliance_operator", authorities = {"report:str", "report:submit"})
    @DisplayName("大额交易报告生成、审核并提交监管")
    void largeTransactionReportFullWorkflow() throws Exception {
        Customer customer = insertCustomer("CTR闭环客户");
        Transaction transaction = insertLargeTransaction(customer.getId());

        MvcResult generateResult = mockMvc.perform(post("/reporting/large-txn/generate")
                        .param("transactionId", String.valueOf(transaction.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reportStatus").value(ReportStatus.DRAFT.getCode()))
                .andExpect(jsonPath("$.data.customerName").value(customer.getName()))
                .andReturn();

        long reportId = responseData(generateResult).path("id").asLong();

        mockMvc.perform(post("/reporting/large-txn/{id}/review", reportId)
                        .param("reviewedBy", "合规复核员"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/reporting/large-txn/{id}/xml", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("<LargeTransactionReport>")));

        mockMvc.perform(post("/reporting/large-txn/{id}/submit", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        LargeTxnReport persisted = largeTxnReportMapper.selectById(reportId);
        assertEquals(ReportStatus.SUBMITTED.getCode(), persisted.getReportStatus());
        assertEquals("compliance_operator", persisted.getSubmittedBy());
        assertTrue(persisted.getXmlContent().contains("<LargeTransactionReport>"));
        assertTrue(persisted.getSubmitResponse().contains("ACCEPTED"));
        assertEquals(1L, reportSubmitLogMapper.selectCount(new LambdaQueryWrapper<ReportSubmitLog>()
                .eq(ReportSubmitLog::getReportType, "LARGE_TXN")
                .eq(ReportSubmitLog::getReportId, reportId)
                .eq(ReportSubmitLog::getSubmitStatus, SubmitStatus.SUCCESS.getCode())));
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private Customer insertCustomer(String name) {
        Customer customer = new Customer();
        customer.setCustomerNo("T-CUST-" + System.nanoTime());
        customer.setCustomerType("INDIVIDUAL");
        customer.setName(name);
        customer.setGender("MALE");
        customer.setNationality("CN");
        customer.setIdType("ID_CARD");
        customer.setIdNumber("11010119900101" + (int) (Math.random() * 9000 + 1000));
        customer.setPhone("13800138000");
        customer.setAddress("北京市朝阳区测试路 88 号");
        customer.setRiskLevel("HIGH");
        customer.setRiskScore(86);
        customer.setKycStatus("COMPLETE");
        customer.setStatus("ACTIVE");
        customer.setCreatedTime(LocalDateTime.now());
        customer.setUpdatedTime(LocalDateTime.now());
        customerMapper.insert(customer);
        return customer;
    }

    private Case insertCase(Customer customer, String status) {
        Case caseEntity = new Case();
        caseEntity.setCaseNo("CASE" + System.nanoTime());
        caseEntity.setCustomerId(customer.getId());
        caseEntity.setCustomerName(customer.getName());
        caseEntity.setCaseStatus(status);
        caseEntity.setCaseType("SUSPICIOUS_TXN");
        caseEntity.setPriority(4);
        caseEntity.setSummary("监管报送测试案件");
        caseEntity.setInvestigatorId(1001L);
        caseEntity.setCreatedBy("1001");
        caseEntity.setCreatedTime(LocalDateTime.now());
        caseEntity.setUpdatedBy("1001");
        caseEntity.setUpdatedTime(LocalDateTime.now());
        caseMapper.insert(caseEntity);
        return caseEntity;
    }

    private Transaction insertLargeTransaction(Long customerId) {
        Transaction transaction = new Transaction();
        transaction.setTransactionNo("TXN-LARGE-" + System.nanoTime());
        transaction.setCustomerId(customerId);
        transaction.setTransactionType("PREMIUM");
        transaction.setAmount(new BigDecimal("520000.00"));
        transaction.setCurrency("CNY");
        transaction.setPaymentMethod("TRANSFER");
        transaction.setChannel("COUNTER");
        transaction.setCounterpartyName("北京华安资产管理有限公司");
        transaction.setCounterpartyAccount("6222000012345678901");
        transaction.setCounterpartyBank("中国工商银行北京分行");
        transaction.setIsCrossBorder(false);
        transaction.setTransactionTime(LocalDateTime.now().minusHours(2));
        transaction.setStatus("SUCCESS");
        transaction.setSourceSystem("CORE");
        transaction.setCreatedTime(LocalDateTime.now());
        transaction.setUpdatedTime(LocalDateTime.now());
        transactionMapper.insert(transaction);
        return transaction;
    }
}
