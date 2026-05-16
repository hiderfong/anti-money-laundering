package com.insurance.aml.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 反洗钱模型管理集成测试。
 */
@DisplayName("模型管理集成测试")
class AmlModelIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("模型全生命周期接口可贯通")
    void modelLifecycleFlow() throws Exception {
        String token = login();

        MvcResult createResult = mockMvc.perform(post("/models")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelCode": "AMLM-IT-001",
                                  "modelName": "集成测试可疑交易评分模型",
                                  "modelType": "HYBRID",
                                  "scenario": "TRANSACTION_MONITORING",
                                  "algorithmType": "RULE_SCORECARD",
                                  "version": "1.0.0",
                                  "owner": "测试管理员",
                                  "governanceLevel": "L1",
                                  "riskLevel": "HIGH",
                                  "trainingDataset": "集成测试训练样本",
                                  "validationDataset": "集成测试验证样本",
                                  "description": "用于验证模型管理生命周期接口"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        long modelId = created.path("id").asLong();
        assertTrue(modelId > 0, "创建模型应返回模型ID");
        assertEquals("DRAFT", created.path("lifecycleStatus").asText());

        mockMvc.perform(post("/models/" + modelId + "/test")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionSummary": "完成验证集回归测试",
                                  "testDataset": "集成测试验证集",
                                  "precisionRate": 0.91,
                                  "recallRate": 0.86,
                                  "falsePositiveRate": 0.11,
                                  "driftScore": 0.04
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("TEST_PASSED")));

        mockMvc.perform(post("/models/" + modelId + "/deploy")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentEnv\":\"UAT\",\"actionSummary\":\"部署至测试环境\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("DEPLOYED")));

        mockMvc.perform(post("/models/" + modelId + "/monitor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitorStatus\":\"NORMAL\",\"falsePositiveRate\":0.10,\"driftScore\":0.03}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("MONITORING")));

        mockMvc.perform(post("/models/" + modelId + "/iterate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetVersion\":\"1.0.1\",\"iterationPlan\":\"补充人工反馈特征\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("ITERATING")));

        MvcResult logResult = mockMvc.perform(get("/models/" + modelId + "/logs?page=1&size=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logs = objectMapper.readTree(logResult.getResponse().getContentAsString()).path("data").path("list");
        assertFalse(logs.isEmpty(), "生命周期动作应写入日志");

        mockMvc.perform(post("/models/" + modelId + "/archive")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiveReason\":\"集成测试归档\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("ARCHIVED")));

        mockMvc.perform(get("/models/page?page=1&size=10&keyword=AMLM-IT-001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("AMLM-IT-001")));
    }

    private String login() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }
}
