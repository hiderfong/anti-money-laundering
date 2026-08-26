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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 连接器建档、健康检查、任务执行和失败重试接口闭环测试。
 */
@DisplayName("集成中心接口测试")
class IntegrationCenterIntegrationTest extends BaseIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("模拟连接器任务可完成自动重试并记录运行日志")
    void integrationExecutionFlow() throws Exception {
        String token = login();
        String suffix = String.valueOf(System.currentTimeMillis()).substring(6);

        JsonNode connector = responseData(authorizedPost(token, "/integrations/connectors", """
                {
                  "connectorCode":"IT_CORE_%s",
                  "connectorName":"集成测试核心业务连接器",
                  "businessType":"CORE_BUSINESS",
                  "transportType":"MOCK",
                  "endpointUrl":"mock://flaky?failures=1&read=120&written=118&skipped=2",
                  "authType":"NONE",
                  "status":"ENABLED",
                  "timeoutSeconds":30,
                  "maxRetries":2,
                  "retryIntervalSeconds":10
                }
                """.formatted(suffix)));
        long connectorId = connector.path("id").asLong();
        assertTrue(connectorId > 0);

        JsonNode healthRun = responseData(authorizedPost(token,
                "/integrations/connectors/" + connectorId + "/test", null));
        assertEquals("SUCCESS", healthRun.path("status").asText());
        assertEquals("HEALTH_CHECK", healthRun.path("triggerType").asText());

        JsonNode job = responseData(authorizedPost(token, "/integrations/jobs", """
                {
                  "jobCode":"IT_SYNC_%s",
                  "jobName":"集成测试交易同步任务",
                  "connectorId":%d,
                  "businessObject":"TRANSACTION",
                  "direction":"INBOUND",
                  "cronExpression":"0 0/30 * * * ?",
                  "batchSize":1000,
                  "maxRetries":2,
                  "enabled":true
                }
                """.formatted(suffix, connectorId)));
        long jobId = job.path("id").asLong();
        assertTrue(jobId > 0);

        JsonNode run = responseData(authorizedPost(token, "/integrations/jobs/" + jobId + "/run", null));
        assertEquals("SUCCESS", run.path("status").asText());
        assertEquals(2, run.path("attemptCount").asInt());
        assertEquals(1, run.path("retryCount").asInt());
        assertEquals(120, run.path("recordsRead").asInt());
        assertEquals(118, run.path("recordsWritten").asInt());

        MvcResult runsResult = mockMvc.perform(get("/integrations/runs/page")
                        .param("page", "1").param("size", "20")
                        .param("connectorId", String.valueOf(connectorId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode runs = responseData(runsResult);
        assertTrue(runs.path("total").asInt() >= 2);
        assertTrue(runs.path("list").get(0).path("connectorName").asText().contains("核心业务"));

        MvcResult overviewResult = mockMvc.perform(get("/integrations/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertTrue(responseData(overviewResult).path("totalConnectors").asInt() >= 1);
    }

    @Test
    @DisplayName("失败运行可由原记录发起人工重试")
    void failedRunCanBeRetried() throws Exception {
        String token = login();
        String suffix = String.valueOf(System.nanoTime()).substring(5);
        JsonNode connector = responseData(authorizedPost(token, "/integrations/connectors", """
                {
                  "connectorCode":"IT_FAIL_%s",
                  "connectorName":"集成测试故障连接器",
                  "businessType":"WATCHLIST",
                  "transportType":"MOCK",
                  "endpointUrl":"mock://failure",
                  "authType":"NONE",
                  "status":"ENABLED",
                  "timeoutSeconds":30,
                  "maxRetries":0,
                  "retryIntervalSeconds":0
                }
                """.formatted(suffix)));
        long connectorId = connector.path("id").asLong();
        JsonNode job = responseData(authorizedPost(token, "/integrations/jobs", """
                {
                  "jobCode":"IT_FAIL_JOB_%s",
                  "jobName":"集成测试失败任务",
                  "connectorId":%d,
                  "businessObject":"WATCHLIST",
                  "direction":"INBOUND",
                  "cronExpression":"0 0 2 * * ?",
                  "batchSize":100,
                  "maxRetries":0,
                  "enabled":true
                }
                """.formatted(suffix, connectorId)));

        JsonNode failedRun = responseData(authorizedPost(token,
                "/integrations/jobs/" + job.path("id").asLong() + "/run", null));
        assertEquals("FAILED", failedRun.path("status").asText());
        long failedRunId = failedRun.path("id").asLong();

        JsonNode retryRun = responseData(authorizedPost(token,
                "/integrations/runs/" + failedRunId + "/retry", null));
        assertEquals("FAILED", retryRun.path("status").asText());
        assertEquals("RETRY", retryRun.path("triggerType").asText());
        assertEquals(failedRunId, retryRun.path("retryOfRunId").asLong());
    }

    private MvcResult authorizedPost(String token, String url, String content) throws Exception {
        var request = post(url).header("Authorization", "Bearer " + token);
        if (content != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(content);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, response.path("code").asInt(), response.path("message").asText());
        return response.path("data");
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }
}
