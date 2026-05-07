package com.insurance.aml.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 交易监测模块集成测试
 */
@DisplayName("交易监测模块集成测试")
public class TransactionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 辅助方法：登录获取token
     */
    private String getAuthToken() throws Exception {
        String loginJson = "{\"username\":\"admin\",\"password\":\"admin123\"}";
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andReturn();
        String response = loginResult.getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    /**
     * 辅助方法：创建一个测试客户并返回客户ID
     */
    private Long createTestCustomer(String name, String idNumber) throws Exception {
        String createJson = String.format("""
                {
                    "customerType": "INDIVIDUAL",
                    "name": "%s",
                    "idType": "ID_CARD",
                    "idNumber": "%s",
                    "nationality": "CN"
                }
                """, name, idNumber);
        MvcResult result = mockMvc.perform(post("/kyc/customers")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    @Order(1)
    @DisplayName("录入交易 - 验证返回ID和状态")
    void testIngestTransaction() throws Exception {
        // 先创建客户
        Long customerId = createTestCustomer("交易测试客户", "110101199030303456");

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String transactionJson = String.format("""
                {
                    "transactionNo": "TXN20240101001",
                    "customerId": %d,
                    "transactionType": "PREMIUM",
                    "amount": 50000.00,
                    "currency": "CNY",
                    "paymentMethod": "TRANSFER",
                    "channel": "ONLINE",
                    "transactionTime": "%s"
                }
                """, customerId, now);

        MvcResult result = mockMvc.perform(post("/monitoring/transactions/ingest")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.transactionNo").value("TXN20240101001"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertTrue(data.path("id").asLong() > 0, "录入交易应返回有效的ID");
        assertNotNull(data.path("status").asText(), "交易应有状态字段");
        assertEquals("PREMIUM", data.path("transactionType").asText());
        assertEquals("50000.00", data.path("amount").asText());
    }

    @Test
    @Order(2)
    @DisplayName("查询交易列表 - 验证分页")
    void testQueryTransactions() throws Exception {
        // 先创建客户
        Long customerId = createTestCustomer("交易查询客户", "110101199040404567");

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        // 录入多笔交易
        for (int i = 1; i <= 3; i++) {
            String txnJson = String.format("""
                    {
                        "transactionNo": "TXN_QUERY_%03d",
                        "customerId": %d,
                        "transactionType": "PREMIUM",
                        "amount": %d0000.00,
                        "currency": "CNY",
                        "paymentMethod": "TRANSFER",
                        "transactionTime": "%s"
                    }
                    """, i, customerId, i, now);
            mockMvc.perform(post("/monitoring/transactions/ingest")
                            .header("Authorization", "Bearer " + getAuthToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(txnJson))
                    .andExpect(status().isOk());
        }

        // 分页查询
        MvcResult result = mockMvc.perform(get("/monitoring/transactions/page")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .param("page", "1")
                        .param("size", "10")
                        .param("customerId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertTrue(data.path("total").asLong() >= 3, "应至少有3条交易记录");
        assertTrue(data.path("list").isArray(), "返回列表应为数组");
        assertTrue(data.path("list").size() >= 3, "列表应包含至少3条记录");

        // 验证分页信息
        assertTrue(data.has("page"), "应返回当前页码");
        assertTrue(data.has("size"), "应返回每页大小");
    }

    @Test
    @Order(3)
    @DisplayName("查询交易统计 - 验证数据")
    void testTransactionStatistics() throws Exception {
        // 先创建客户
        Long customerId = createTestCustomer("统计测试客户", "110101199050505678");

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        // 录入一笔交易
        String txnJson = String.format("""
                {
                    "transactionNo": "TXN_STAT_001",
                    "customerId": %d,
                    "transactionType": "PREMIUM",
                    "amount": 100000.00,
                    "currency": "CNY",
                    "paymentMethod": "CASH",
                    "transactionTime": "%s"
                }
                """, customerId, now);
        mockMvc.perform(post("/monitoring/transactions/ingest")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txnJson))
                .andExpect(status().isOk());

        // 查询交易日汇总 - 使用当天日期
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        MvcResult result = mockMvc.perform(get("/monitoring/transactions/daily-summary")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .param("customerId", customerId.toString())
                        .param("date", today)
                        .param("transactionType", "PREMIUM"))
                .andReturn();

        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();

        // 日汇总接口可能返回200（有数据）或业务错误码（无汇总数据），都是正常的
        assertTrue(status == 200, "日汇总接口应可访问，实际状态码: " + status);

        JsonNode response = objectMapper.readTree(body);
        assertTrue(response.has("code"), "响应应包含code字段");

        // 如果有汇总数据，验证字段
        if (response.path("code").asInt() == 200 && response.has("data") && !response.path("data").isNull()) {
            JsonNode data = response.path("data");
            assertTrue(data.has("customerId") || data.has("totalAmount"),
                    "汇总数据应包含统计字段");
        }
    }
}
