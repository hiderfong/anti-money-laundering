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
 * 客户管理模块集成测试
 */
@DisplayName("客户管理模块集成测试")
public class CustomerIntegrationTest extends BaseIntegrationTest {

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
     * 辅助方法：创建一个测试客户并返回响应body
     */
    private String createTestCustomer(String name, String idNumber) throws Exception {
        String createJson = String.format("""
                {
                    "customerType": "INDIVIDUAL",
                    "name": "%s",
                    "gender": "MALE",
                    "nationality": "CN",
                    "idType": "ID_CARD",
                    "idNumber": "%s",
                    "phone": "13800138000",
                    "email": "test@example.com"
                }
                """, name, idNumber);
        MvcResult result = mockMvc.perform(post("/kyc/customers")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    @Order(1)
    @DisplayName("创建客户 - 验证返回ID")
    void testCreateCustomer() throws Exception {
        String createJson = """
                {
                    "customerType": "INDIVIDUAL",
                    "name": "张三",
                    "gender": "MALE",
                    "nationality": "CN",
                    "idType": "ID_CARD",
                    "idNumber": "110101199001011234",
                    "phone": "13800138000",
                    "email": "zhangsan@example.com"
                }
                """;

        MvcResult result = mockMvc.perform(post("/kyc/customers")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("张三"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertTrue(data.path("id").asLong() > 0, "创建客户应返回有效的ID");
        assertEquals("INDIVIDUAL", data.path("customerType").asText());
    }

    @Test
    @Order(2)
    @DisplayName("查询客户详情 - 验证字段")
    void testGetCustomerDetail() throws Exception {
        // 先创建客户
        String createResponse = createTestCustomer("李四", "110101199002022345");
        Long customerId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        // 查询详情
        MvcResult result = mockMvc.perform(get("/kyc/customers/" + customerId)
                        .header("Authorization", "Bearer " + getAuthToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(customerId))
                .andExpect(jsonPath("$.data.name").value("李*"))
                .andExpect(jsonPath("$.data.idNumber").value("1101**********2345"))
                .andExpect(jsonPath("$.data.customerType").value("INDIVIDUAL"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(customerId, data.path("id").asLong());
        assertEquals("李*", data.path("name").asText());
        assertEquals("138****8000", data.path("phone").asText());
    }

    @Test
    @Order(3)
    @DisplayName("更新客户信息 - 验证更新")
    void testUpdateCustomer() throws Exception {
        // 先创建客户
        String createResponse = createTestCustomer("王五", "110101199003033456");
        Long customerId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        // 更新客户信息
        String updateJson = String.format("""
                {
                    "id": %d,
                    "nameEn": "Wang Wu",
                    "phone": "13900139000",
                    "email": "wangwu@example.com",
                    "occupation": "工程师"
                }
                """, customerId);

        MvcResult result = mockMvc.perform(put("/kyc/customers")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        // 验证更新后的数据
        mockMvc.perform(get("/kyc/customers/" + customerId)
                .header("Authorization", "Bearer " + getAuthToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nameEn").value("Wang Wu"))
                .andExpect(jsonPath("$.data.phone").value("139****9000"))
                .andExpect(jsonPath("$.data.email").value("wangwu@example.com"));
    }

    @Test
    @Order(4)
    @DisplayName("分页查询客户列表 - 验证总数")
    void testListCustomers() throws Exception {
        // 创建多个客户
        createTestCustomer("测试客户A", "110101199004044567");
        createTestCustomer("测试客户B", "110101199005055678");
        createTestCustomer("测试客户C", "110101199006066789");

        // 分页查询
        MvcResult result = mockMvc.perform(get("/kyc/customers/page")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.total").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertTrue(data.path("total").asLong() >= 3, "应至少有3条客户记录");
        assertTrue(data.path("list").isArray(), "返回列表应为数组");
        assertTrue(data.path("list").size() >= 3, "列表应包含至少3条记录");
    }

    @Test
    @Order(5)
    @DisplayName("按姓名搜索客户 - 验证结果")
    void testSearchCustomers() throws Exception {
        // 创建特定名称的客户
        createTestCustomer("搜索测试赵六", "110101199007077890");

        // 按姓名搜索
        MvcResult result = mockMvc.perform(get("/kyc/customers/page")
                        .header("Authorization", "Bearer " + getAuthToken())
                        .param("page", "1")
                        .param("size", "10")
                        .param("name", "搜索测试赵六"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertTrue(data.path("total").asLong() >= 1, "按姓名搜索应返回至少1条记录");

        // 验证搜索结果中包含目标客户
        boolean found = false;
        for (JsonNode item : data.path("list")) {
            if ("搜索测试赵六".equals(item.path("name").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "搜索结果应包含目标客户'搜索测试赵六'");
    }
}
