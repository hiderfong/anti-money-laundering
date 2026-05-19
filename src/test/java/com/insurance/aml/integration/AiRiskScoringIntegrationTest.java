package com.insurance.aml.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI辅助反洗钱风险评分集成测试。
 */
@DisplayName("AI风险评分模块集成测试")
public class AiRiskScoringIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String getAuthToken() throws Exception {
        String loginJson = "{\"username\":\"admin\",\"password\":\"admin123\"}";
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andReturn();
        String response = loginResult.getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private Long createCustomer() throws Exception {
        String createJson = """
                {
                    "customerType": "INDIVIDUAL",
                    "name": "AI风险评分客户",
                    "gender": "MALE",
                    "nationality": "CN",
                    "idType": "ID_CARD",
                    "idNumber": "110101198801019999",
                    "phone": "13800139999",
                    "email": "ai-risk@example.com",
                    "occupation": "贵金属交易从业人员"
                }
                """;
        MvcResult result = mockMvc.perform(post("/kyc/customers")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isOk())
                .andReturn();
        Long customerId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
        jdbcTemplate.update("""
                UPDATE t_customer
                SET risk_level = 'HIGH',
                    risk_score = 78,
                    is_pep = TRUE,
                    kyc_status = 'COMPLETE'
                WHERE id = ?
                """, customerId);
        return customerId;
    }

    private Long createTransaction(Long customerId) throws Exception {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String transactionJson = String.format("""
                {
                    "transactionNo": "TXN_AI_RISK_001",
                    "customerId": %d,
                    "transactionType": "SURRENDER",
                    "amount": 260000.00,
                    "currency": "CNY",
                    "paymentMethod": "CASH",
                    "counterpartyName": "上海启辰贸易有限公司",
                    "counterpartyAccount": "622200000000009999",
                    "counterpartyBank": "招商银行上海分行",
                    "isCrossBorder": true,
                    "transactionTime": "%s"
                }
                """, customerId, now);
        MvcResult result = mockMvc.perform(post("/monitoring/transactions/ingest")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private Long createAlert(Long customerId, Long transactionId) {
        jdbcTemplate.update("""
                INSERT INTO t_alert (
                    alert_no, customer_id, customer_name, alert_type, risk_score, risk_level,
                    source_rule_codes, alert_summary, status, process_result, related_transaction_ids,
                    created_time, updated_time
                ) VALUES (
                    'ALT_AI_RISK_001', ?, 'AI风险评分客户', 'SUSPICIOUS', 88, 'HIGH',
                    'AI_BASELINE_TEST', 'AI风险评分集成测试预警', 'PROCESSING', 'CONFIRMED_SUSPICIOUS', ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, customerId, String.valueOf(transactionId));
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_alert WHERE alert_no = 'ALT_AI_RISK_001'", Long.class);
    }

    @Test
    @Order(1)
    @DisplayName("AI风险评分 - 客户、交易、预警接口均返回可解释结果")
    void testAiRiskScoringEndpoints() throws Exception {
        String token = getAuthToken();
        Long customerId = createCustomer();
        Long transactionId = createTransaction(customerId);
        Long alertId = createAlert(customerId, transactionId);

        MvcResult customerResult = mockMvc.perform(get("/ai/risk/customers/" + customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.subjectType").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.scoreNo").exists())
                .andExpect(jsonPath("$.data.modelId").value("100"))
                .andExpect(jsonPath("$.data.modelVersion").value("1.0.0"))
                .andExpect(jsonPath("$.data.score").isNumber())
                .andExpect(jsonPath("$.data.riskLevel").exists())
                .andExpect(jsonPath("$.data.factors").isArray())
                .andReturn();
        JsonNode customerScore = objectMapper.readTree(customerResult.getResponse().getContentAsString()).path("data");
        assertFalse(customerScore.path("factors").isEmpty(), "客户AI评分应返回贡献因子");
        assertTrue(customerScore.path("confidence").asInt() > 0, "客户AI评分应返回置信度");

        mockMvc.perform(get("/ai/risk/transactions/" + transactionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectType").value("TRANSACTION"))
                .andExpect(jsonPath("$.data.scoreNo").exists())
                .andExpect(jsonPath("$.data.featureSummary.amountToAverageRatio").exists())
                .andExpect(jsonPath("$.data.recommendations").isArray());

        mockMvc.perform(get("/ai/risk/alerts/" + alertId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectType").value("ALERT"))
                .andExpect(jsonPath("$.data.scoreNo").exists())
                .andExpect(jsonPath("$.data.factors").isArray())
                .andExpect(jsonPath("$.data.evidence").isArray());

        mockMvc.perform(get("/ai/risk/customers/" + customerId + "/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].scoreNo").exists())
                .andExpect(jsonPath("$.data[0].modelVersion").value("1.0.0"));

        MvcResult modelStatusResult = mockMvc.perform(get("/ai/risk/model-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelCode").value("AI_AML_RISK_BASELINE_V1"))
                .andReturn();
        JsonNode modelStatus = objectMapper.readTree(modelStatusResult.getResponse().getContentAsString()).path("data");
        assertTrue(modelStatus.path("scoringRecordCount").asLong() >= 3, "模型状态应统计已落库评分记录");

        mockMvc.perform(get("/ai/risk/review-pool/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalScores").exists())
                .andExpect(jsonPath("$.data.pendingReviewCount").exists());

        MvcResult reviewPoolResult = mockMvc.perform(get("/ai/risk/review-pool")
                        .param("minScore", "65")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].scoreNo").exists())
                .andExpect(jsonPath("$.data.list[0].autoLabel").exists())
                .andExpect(jsonPath("$.data.list[0].priorityLevel").exists())
                .andExpect(jsonPath("$.data.list[0].verificationBasis").exists())
                .andReturn();

        Long reviewRecordId = objectMapper.readTree(reviewPoolResult.getResponse().getContentAsString())
                .path("data")
                .path("list")
                .path(0)
                .path("id")
                .asLong();
        String reviewJson = """
                {
                    "reviewLabel": "TRUE_POSITIVE",
                    "reviewComment": "测试确认该评分可作为弱正样本留痕"
                }
                """;
        mockMvc.perform(post("/ai/risk/review-pool/" + reviewRecordId + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manualReviewLabel").value("TRUE_POSITIVE"))
                .andExpect(jsonPath("$.data.reviewStatus").value("MANUAL_REVIEWED"));

        MvcResult exportResult = mockMvc.perform(get("/ai/risk/review-pool/export")
                        .param("minScore", "65")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String csv = exportResult.getResponse().getContentAsString();
        assertTrue(csv.contains("评分流水号"), "导出的复核清单应包含CSV表头");
        assertTrue(csv.contains("确认有效风险"), "导出的复核清单应包含人工复核标签");
    }
}
