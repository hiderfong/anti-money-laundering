package com.insurance.aml.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.common.enums.AssessmentStatusEnum;
import com.insurance.aml.common.enums.RectificationStatus;
import com.insurance.aml.module.assessment.mapper.AssessmentIndicatorMapper;
import com.insurance.aml.module.assessment.mapper.RectificationTaskMapper;
import com.insurance.aml.module.assessment.model.entity.AssessmentIndicator;
import com.insurance.aml.module.assessment.model.entity.RectificationTask;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.prevention.mapper.FreezeSeizureDeductionMapper;
import com.insurance.aml.module.prevention.mapper.RetrospectiveScreeningJobMapper;
import com.insurance.aml.module.prevention.mapper.SpecialMeasureMapper;
import com.insurance.aml.module.prevention.model.entity.FreezeSeizureDeduction;
import com.insurance.aml.module.prevention.model.entity.RetrospectiveScreeningJob;
import com.insurance.aml.module.prevention.model.entity.SpecialMeasure;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 风险自评估、整改与特别预防关键流程集成测试。
 */
@DisplayName("风险自评估与特别预防关键流程集成测试")
class AssessmentPreventionWorkflowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AssessmentIndicatorMapper indicatorMapper;
    @Autowired
    private RectificationTaskMapper rectificationTaskMapper;
    @Autowired
    private CustomerMapper customerMapper;
    @Autowired
    private SpecialMeasureMapper specialMeasureMapper;
    @Autowired
    private FreezeSeizureDeductionMapper freezeMapper;
    @Autowired
    private RetrospectiveScreeningJobMapper retrospectiveJobMapper;

    @Test
    @WithMockUser(username = "assessment_admin", authorities = {"assessment:manage", "assessment:view"})
    @DisplayName("自评估评分、完成并审批")
    void selfAssessmentScoreCompleteAndApprove() throws Exception {
        AssessmentIndicator inherent = insertIndicator("INHERENT_RISK", "客户风险暴露", new BigDecimal("0.60"));
        AssessmentIndicator control = insertIndicator("CONTROL_EFFECTIVENESS", "控制措施有效性", new BigDecimal("0.40"));

        MvcResult createResult = mockMvc.perform(post("/assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assessmentYear": 2026,
                                  "assessmentPeriod": "ANNUAL",
                                  "assessorId": 1001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assessmentStatus").value(AssessmentStatusEnum.CREATED.getCode()))
                .andReturn();
        long assessmentId = responseData(createResult).path("id").asLong();

        submitScore(assessmentId, inherent.getId(), 85, "高风险客户占比较高");
        submitScore(assessmentId, control.getId(), 55, "部分控制措施仍需优化");

        mockMvc.perform(post("/assessments/{id}/complete", assessmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assessmentStatus").value(AssessmentStatusEnum.COMPLETED.getCode()))
                .andExpect(jsonPath("$.data.overallScore").value(73))
                .andExpect(jsonPath("$.data.overallRiskLevel").value("HIGH"));

        mockMvc.perform(post("/assessments/{id}/approve", assessmentId)
                        .param("approvedBy", "合规负责人"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/assessments/{id}", assessmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assessmentStatus").value(AssessmentStatusEnum.APPROVED.getCode()))
                .andExpect(jsonPath("$.data.scores.length()").value(2));
    }

    @Test
    @WithMockUser(username = "assessment_admin", authorities = {"assessment:manage"})
    @DisplayName("整改任务创建、完成并验证")
    void rectificationCreateCompleteAndVerify() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/assessments/rectifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType": "SELF_ASSESSMENT",
                                  "issueDescription": "高风险客户复核台账存在缺项",
                                  "issueCategory": "KYC_REVIEW",
                                  "severity": "HIGH",
                                  "responsibleDept": "合规管理部",
                                  "responsiblePerson": "e2e_compliance",
                                  "deadline": "%s"
                                }
                                """.formatted(LocalDate.now().plusDays(7))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(RectificationStatus.OPEN.getCode()))
                .andExpect(jsonPath("$.data.progressPercent").value(0))
                .andReturn();
        long taskId = responseData(createResult).path("id").asLong();

        mockMvc.perform(put("/assessments/rectifications/{id}/status", taskId)
                        .param("status", RectificationStatus.COMPLETED.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        RectificationTask completed = rectificationTaskMapper.selectById(taskId);
        assertEquals(100, completed.getProgressPercent());
        assertNotNull(completed.getCompletedTime());

        mockMvc.perform(post("/assessments/rectifications/{id}/verify", taskId)
                        .param("verifiedBy", "e2e_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        RectificationTask verified = rectificationTaskMapper.selectById(taskId);
        assertEquals(RectificationStatus.VERIFIED.getCode(), verified.getStatus());
        assertEquals("刘思远", verified.getVerifiedBy());
        assertNotNull(verified.getClosedTime());
    }

    @Test
    @WithMockUser(username = "special_admin", authorities = {"special:view", "special:manage"})
    @DisplayName("特别预防措施、查冻扣和回溯筛查任务")
    void specialPreventionMeasureFreezeAndRetrospectiveJob() throws Exception {
        Customer customer = insertCustomer("特别预防测试客户");

        MvcResult measureResult = mockMvc.perform(post("/special-prevention/measures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "measureType": "ENHANCED_DUE_DILIGENCE",
                                  "triggerType": "HIGH_RISK_CUSTOMER",
                                  "controlLevel": "HIGH",
                                  "measureContent": "提高客户尽调频率，并限制大额现金交易。",
                                  "startDate": "%s",
                                  "decisionReason": "客户风险评级为高风险。"
                                }
                                """.formatted(customer.getId(), LocalDate.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.customerName").value(customer.getName()))
                .andReturn();
        long measureId = responseData(measureResult).path("id").asLong();

        mockMvc.perform(put("/special-prevention/measures/{id}/status", measureId)
                        .param("status", "CLOSED")
                        .param("reason", "复核后风险已降至可接受水平"))
                .andExpect(status().isOk());
        SpecialMeasure measure = specialMeasureMapper.selectById(measureId);
        assertEquals("CLOSED", measure.getStatus());
        assertTrue(measure.getClosedReason().contains("风险已降"));

        MvcResult freezeResult = mockMvc.perform(post("/special-prevention/freeze-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "authorityName": "北京市公安局经侦总队",
                                  "documentNo": "JD-2026-0001",
                                  "actionType": "FREEZE",
                                  "amount": 300000.00,
                                  "currency": "CNY",
                                  "effectiveDate": "%s",
                                  "handler": "周明哲",
                                  "remark": "司法协查冻结"
                                }
                                """.formatted(customer.getId(), LocalDate.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        long freezeId = responseData(freezeResult).path("id").asLong();

        mockMvc.perform(put("/special-prevention/freeze-records/{id}/status", freezeId)
                        .param("status", "RELEASED")
                        .param("remark", "司法机关解除冻结"))
                .andExpect(status().isOk());
        FreezeSeizureDeduction freeze = freezeMapper.selectById(freezeId);
        assertEquals("RELEASED", freeze.getStatus());
        assertTrue(freeze.getRemark().contains("解除冻结"));

        MvcResult retrospectiveResult = mockMvc.perform(post("/special-prevention/retrospective-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobName": "指定客户名单回溯筛查",
                                  "scopeType": "CUSTOMER_IDS",
                                  "customerIds": "%d",
                                  "remark": "验证指定客户范围回溯筛查任务"
                                }
                                """.formatted(customer.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalCustomers").value(1))
                .andReturn();
        long jobId = responseData(retrospectiveResult).path("id").asLong();

        RetrospectiveScreeningJob job = retrospectiveJobMapper.selectById(jobId);
        assertEquals(1, job.getProcessedCustomers());
        assertNotNull(job.getCompletedTime());

        mockMvc.perform(get("/special-prevention/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeRetrospectiveJobs").value(0));
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void submitScore(long assessmentId, long indicatorId, int score, String evidence) throws Exception {
        mockMvc.perform(post("/assessments/score")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assessmentId": %d,
                                  "indicatorId": %d,
                                  "rawValue": %d,
                                  "score": %d,
                                  "evidence": "%s",
                                  "dataSource": "E2E_TEST",
                                  "remark": "关键路径测试评分"
                                }
                                """.formatted(assessmentId, indicatorId, score, score, evidence)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private AssessmentIndicator insertIndicator(String category, String name, BigDecimal weight) {
        AssessmentIndicator indicator = new AssessmentIndicator();
        indicator.setIndicatorCode("IND-" + category + "-" + System.nanoTime());
        indicator.setIndicatorName(name);
        indicator.setCategory(category);
        indicator.setDimension("TEST");
        indicator.setWeight(weight);
        indicator.setMaxScore(100);
        indicator.setStatus("ENABLED");
        indicator.setCreatedTime(LocalDateTime.now());
        indicator.setUpdatedTime(LocalDateTime.now());
        indicatorMapper.insert(indicator);
        return indicator;
    }

    private Customer insertCustomer(String name) {
        Customer customer = new Customer();
        customer.setCustomerNo("T-CUST-" + System.nanoTime());
        customer.setCustomerType("INDIVIDUAL");
        customer.setName(name);
        customer.setGender("MALE");
        customer.setNationality("CN");
        customer.setIdType("ID_CARD");
        customer.setIdNumber("11010119881212" + (int) (Math.random() * 9000 + 1000));
        customer.setPhone("13900139000");
        customer.setAddress("上海市浦东新区测试路 66 号");
        customer.setRiskLevel("HIGH");
        customer.setRiskScore(88);
        customer.setKycStatus("COMPLETE");
        customer.setStatus("ACTIVE");
        customer.setCreatedTime(LocalDateTime.now());
        customer.setUpdatedTime(LocalDateTime.now());
        customerMapper.insert(customer);
        return customer;
    }
}
