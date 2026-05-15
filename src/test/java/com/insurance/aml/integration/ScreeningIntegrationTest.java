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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 名单筛查模块集成测试
 */
@DisplayName("名单筛查模块集成测试")
public class ScreeningIntegrationTest extends BaseIntegrationTest {

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
    @DisplayName("筛查客户 - 验证筛查结果")
    void testScreenCustomer() throws Exception {
        // 先创建一个客户
        Long customerId = createTestCustomer("制裁测试客户", "110101199010101234");

        // 触发筛查
        String screeningJson = String.format("""
                {
                    "customerId": %d,
                    "screeningType": "CUSTOMER_ONBOARD"
                }
                """, customerId);

        MvcResult result = mockMvc.perform(post("/screening/screen")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        // 筛查应返回命中数量（可能是0或更多）
        assertTrue(data.isNumber() || data.asText().matches("\\d+"), "筛查结果应为命中数量");
    }

    @Test
    @Order(2)
    @DisplayName("查询筛查结果 - 验证命中")
    void testGetScreeningResult() throws Exception {
        // 先创建客户并触发筛查
        Long customerId = createTestCustomer("筛查查询客户", "110101199020202345");

        String screeningJson = String.format("""
                {
                    "customerId": %d,
                    "screeningType": "CUSTOMER_ONBOARD"
                }
                """, customerId);

        mockMvc.perform(post("/screening/screen")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningJson))
                .andExpect(status().isOk());

        // 查询筛查结果
        MvcResult result = mockMvc.perform(get("/screening/results")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertTrue(data.has("total"), "应返回分页总数");
        assertTrue(data.has("list"), "应返回结果列表");

        // 如果有命中结果，验证字段完整性
        if (data.path("total").asLong() > 0) {
            JsonNode firstResult = data.path("list").get(0);
            assertTrue(firstResult.has("id"), "筛查结果应包含id字段");
            assertTrue(firstResult.has("customerId"), "筛查结果应包含customerId字段");
            assertTrue(firstResult.has("matchScore"), "筛查结果应包含matchScore字段");
            assertTrue(firstResult.has("matchType"), "筛查结果应包含matchType字段");
            assertTrue(firstResult.has("reviewStatus"), "筛查结果应包含reviewStatus字段");
        }
    }
}
