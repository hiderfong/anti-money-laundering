package com.insurance.aml.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 健康检查集成测试
 */
@DisplayName("健康检查集成测试")
public class HealthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("健康检查接口可访问 - 返回JSON响应")
    void healthCheckAccessible() throws Exception {
        // MockMvc 默认不带context-path，直接用servletPath
        mockMvc.perform(get("/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.databaseUrl").doesNotExist());
    }

    @Test
    @DisplayName("系统信息接口可访问 - 返回版本信息")
    void systemInfoAccessible() throws Exception {
        mockMvc.perform(get("/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.javaVersion").exists());
    }

    @Test
    @DisplayName("Actuator health 可匿名访问")
    void actuatorHealthAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 200 || status == 503, "health端点应匿名可达，但不应被认证拦截");
                })
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Actuator env 不允许匿名访问")
    void actuatorEnvRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Actuator metrics 不允许匿名访问")
    void actuatorMetricsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }
}
