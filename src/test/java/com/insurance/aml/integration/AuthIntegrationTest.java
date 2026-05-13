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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证模块集成测试
 */
@DisplayName("认证模块集成测试")
public class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("登录接口可访问 - 返回响应")
    void loginEndpointAccessible() throws Exception {
        String loginJson = "{\"username\":\"admin\",\"password\":\"Aml@Admin#2026!\"}";

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andReturn();

        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();

        assertEquals(200, status, "admin/Aml@Admin#2026! 应登录成功，实际 body: " + body);
        assertTrue(body.contains("accessToken"), "成功登录应返回accessToken");

        JsonNode data = objectMapper.readTree(body).path("data");
        assertRolesAndPermissions(data);
    }

    @Test
    @Order(2)
    @DisplayName("访问受保护资源 - 无token返回401")
    void accessProtectedWithoutToken() throws Exception {
        mockMvc.perform(get("/kyc/customers/page"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    @DisplayName("登录后访问受保护资源")
    void accessProtectedWithToken() throws Exception {
        // 先登录获取token
        String loginJson = "{\"username\":\"admin\",\"password\":\"Aml@Admin#2026!\"}";
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andReturn();

        String response = loginResult.getResponse().getContentAsString();
        int loginStatus = loginResult.getResponse().getStatus();

        assertEquals(200, loginStatus, "admin/Aml@Admin#2026! 应登录成功，实际 body: " + response);

        // 提取token
        assertTrue(response.contains("accessToken"), "应包含accessToken");
        String token = objectMapper.readTree(response).path("data").path("accessToken").asText();
        assertNotNull(token, "token不应为空");

        // 使用token访问受保护资源
        mockMvc.perform(get("/kyc/customers/page")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    @DisplayName("刷新Token - 返回角色和权限")
    void refreshTokenReturnsRolesAndPermissions() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Aml@Admin#2026!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginData = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data");
        String refreshToken = loginData.path("refreshToken").asText();
        assertFalse(refreshToken.isBlank(), "登录响应应包含refreshToken");

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(refreshResult.getResponse().getContentAsString()).path("data");
        assertTrue(data.hasNonNull("accessToken"), "刷新响应应返回accessToken");
        assertTrue(data.hasNonNull("refreshToken"), "刷新响应应返回refreshToken");
        assertRolesAndPermissions(data);
    }

    @Test
    @Order(5)
    @DisplayName("当前用户接口 - 返回前端权限契约")
    void currentUserReturnsFrontendPermissionContract() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Aml@Admin#2026!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        MvcResult meResult = mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(meResult.getResponse().getContentAsString()).path("data");
        assertEquals("admin", data.path("username").asText());
        assertFalse(data.has("password"), "当前用户接口不应返回密码字段");
        assertFalse(data.has("authorities"), "当前用户接口应返回前端契约字段，而不是Spring Security authorities");
        assertRolesAndPermissions(data);
    }

    private void assertRolesAndPermissions(JsonNode data) {
        assertTrue(data.has("roles"), "响应应返回roles字段");
        assertTrue(data.path("roles").isArray(), "roles应为数组");
        assertTrue(data.path("roles").toString().contains("ROLE_ADMIN"), "admin应包含ROLE_ADMIN角色");
        assertTrue(data.has("permissions"), "响应应返回permissions字段");
        assertTrue(data.path("permissions").isArray(), "permissions应为数组");
        assertFalse(data.path("permissions").isEmpty(), "admin权限列表不应为空");
    }
}
