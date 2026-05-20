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

import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
@TestPropertySource(properties = "aml.ml.ai-risk.min-samples=4")
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

    @Test
    @Order(2)
    @DisplayName("复核标注回流-训练-影子评分闭环")
    void supervisedFeedbackLoop_endToEnd() throws Exception {
        String token = getAuthToken();

        // 1) 种入带标签的评分记录（正负各15条），喂给监督训练。
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String now = LocalDateTime.now().format(fmt);
        for (int i = 0; i < 30; i++) {
            boolean positive = (i % 2 == 0);
            String label = positive ? "TRUE_POSITIVE" : "FALSE_POSITIVE";
            String feat = positive
                    ? "{\"transactionCount90d\":20,\"kycCompleteness\":30,\"highRiskAlertCount\":3}"
                    : "{\"transactionCount90d\":1,\"kycCompleteness\":95,\"highRiskAlertCount\":0}";
            jdbcTemplate.update(
                    "INSERT INTO t_ai_risk_score_record " +
                    "(score_no, subject_type, subject_id, subject_name, customer_id, " +
                    "model_code, model_name, model_version, score, risk_level, confidence, " +
                    "factor_summary, feature_snapshot_json, scored_at, " +
                    "manual_review_label, reviewed_by, reviewed_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "SEED" + i,
                    "CUSTOMER",
                    (long) (1000 + i),
                    "种子客户" + i,
                    (long) (1000 + i),
                    "AI_AML_RISK_BASELINE_V1",
                    "AI可解释风险评分基线模型",
                    "1.0.0",
                    positive ? 80 : 10,
                    positive ? "HIGH" : "LOW",
                    70,
                    "种子样本",
                    feat,
                    now,
                    label,
                    "admin",
                    now);
        }

        // 2) 触发训练。
        MvcResult retrain = mockMvc.perform(post("/ai/risk/model/retrain")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rb = objectMapper.readTree(retrain.getResponse().getContentAsString()).path("data");
        assertEquals("TRAINED", rb.path("status").asText(), "训练应返回TRAINED状态");
        // 下界断言防止parseVector静默丢样本；允许前序测试遗留的已复核记录被一并消费。
        assertTrue(rb.path("sampleCount").asInt() >= 30, "训练应至少消费30条种子样本");
        assertTrue(rb.path("positiveCount").asInt() >= 15, "正样本应至少15条");
        assertTrue(rb.path("negativeCount").asInt() >= 15, "负样本应至少15条");
        assertTrue(rb.path("modelReady").asBoolean(), "训练后模型应就绪");

        // 3) 训练状态接口应反映已就绪。
        mockMvc.perform(get("/ai/risk/model/training-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TRAINED"))
                .andExpect(jsonPath("$.data.modelReady").value(true));

        // 4) 对新客户评分，落库记录应带上影子概率分。
        Long customerId = createCustomer();
        mockMvc.perform(get("/ai/risk/customers/" + customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        Double prob = jdbcTemplate.queryForObject(
                "SELECT model_probability FROM t_ai_risk_score_record " +
                "WHERE customer_id = ? ORDER BY id DESC LIMIT 1",
                Double.class, customerId);
        assertTrue(prob != null && prob >= 0.0 && prob <= 1.0,
                "评分落库应包含 [0,1] 区间的影子概率分");
        String labelPred = jdbcTemplate.queryForObject(
                "SELECT model_label_predicted FROM t_ai_risk_score_record " +
                "WHERE customer_id = ? ORDER BY id DESC LIMIT 1",
                String.class, customerId);
        assertTrue("SUSPICIOUS".equals(labelPred) || "NORMAL".equals(labelPred),
                "影子预测标签应为 SUSPICIOUS 或 NORMAL");
    }
}
