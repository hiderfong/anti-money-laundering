package com.insurance.aml.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 后端权限集成测试。
 */
@DisplayName("RBAC后端权限集成测试")
public class RbacIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "viewer", authorities = {"ROLE_VIEWER", "customer:view"})
    @DisplayName("只读用户调用客户创建接口 -> 403")
    void viewerCannotCreateCustomer() throws Exception {
        mockMvc.perform(post("/kyc/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson("RBAC拒绝客户", "110101199901019999")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(username = "customer_operator", authorities = {"customer:create"})
    @DisplayName("拥有 customer:create 权限 -> 可创建客户")
    void permissionAuthorityCanCreateCustomer() throws Exception {
        mockMvc.perform(post("/kyc/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson("RBAC允许客户", "110101199901018888")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    @WithMockUser(username = "investigator", authorities = {"ROLE_INVESTIGATOR", "customer:create"})
    @DisplayName("调查员调用产品创建接口 -> 403")
    void investigatorCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "RBAC_BLOCKED_PRODUCT",
                                  "productName": "RBAC拒绝产品",
                                  "productType": "LIFE"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(username = "compliance", authorities = {"ROLE_COMPLIANCE", "product:manage"})
    @DisplayName("合规专员调用系统用户创建接口 -> 403")
    void complianceCannotCreateSystemUser() throws Exception {
        mockMvc.perform(post("/system/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "rbac_blocked_user",
                                  "password": "admin123",
                                  "realName": "RBAC拒绝用户"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(username = "viewer", authorities = {"ROLE_VIEWER", "customer:view"})
    @DisplayName("只读用户调用审计日志接口 -> 403")
    void viewerCannotReadAuditLogs() throws Exception {
        mockMvc.perform(get("/system/audit-logs/page")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(username = "system_operator", authorities = {"system:view"})
    @DisplayName("拥有 system:view 权限 -> 可查询审计日志")
    void systemViewCanReadAuditLogs() throws Exception {
        mockMvc.perform(get("/system/audit-logs/page")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(username = "viewer", authorities = {"ROLE_VIEWER", "customer:view"})
    @DisplayName("只读用户调用交易图分析接口 -> 403")
    void viewerCannotReadGraphAnalysis() throws Exception {
        mockMvc.perform(get("/monitoring/graph/density/1")
                        .param("densityThreshold", "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(username = "monitoring_operator", authorities = {"monitoring:view"})
    @DisplayName("拥有 monitoring:view 权限 -> 可查询交易图分析")
    void monitoringViewCanReadGraphAnalysis() throws Exception {
        mockMvc.perform(get("/monitoring/graph/density/1")
                        .param("densityThreshold", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(username = "viewer", authorities = {"ROLE_VIEWER", "customer:view"})
    @DisplayName("只读用户调用规则反馈接口 -> 403")
    void viewerCannotReadRuleFeedback() throws Exception {
        mockMvc.perform(get("/monitoring/rules/feedback/summary"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(username = "monitoring_operator", authorities = {"monitoring:view"})
    @DisplayName("拥有 monitoring:view 权限 -> 可查询规则反馈")
    void monitoringViewCanReadRuleFeedback() throws Exception {
        mockMvc.perform(get("/monitoring/rules/feedback/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private String customerJson(String name, String idNumber) {
        return """
                {
                  "customerType": "INDIVIDUAL",
                  "name": "%s",
                  "gender": "MALE",
                  "nationality": "CN",
                  "idType": "ID_CARD",
                  "idNumber": "%s",
                  "phone": "13800138000",
                  "email": "rbac@example.com"
                }
                """.formatted(name, idNumber);
    }
}
