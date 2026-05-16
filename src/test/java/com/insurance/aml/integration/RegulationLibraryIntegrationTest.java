package com.insurance.aml.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 法规及资料库集成测试。
 */
@DisplayName("法规及资料库集成测试")
class RegulationLibraryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("法规资料分类、全文检索、动态查询和发布归档可贯通")
    void regulationLibraryFlow() throws Exception {
        String token = login();

        MvcResult categoryResult = mockMvc.perform(post("/regulation-library/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryCode": "IT-REG-CATEGORY",
                                  "categoryName": "集成测试法规",
                                  "categoryType": "REGULATION",
                                  "sortOrder": 99,
                                  "description": "用于验证法规资料库分类管理"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        MvcResult createResult = mockMvc.perform(post("/regulation-library/documents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "docCode": "AML-KB-IT-001",
                                  "title": "集成测试客户尽职调查法规资料",
                                  "docType": "REGULATION",
                                  "categoryId": %d,
                                  "sourceType": "REGULATOR",
                                  "sourceOrg": "测试监管机构",
                                  "publishDate": "2026-05-15",
                                  "effectiveDate": "2026-06-01",
                                  "importantFlag": true,
                                  "summary": "覆盖客户尽职调查和资料保存要求",
                                  "content": "客户尽职调查、受益所有人识别、可疑交易报告、交易记录保存均应纳入反洗钱知识库全文检索。",
                                  "tags": "客户尽职调查,受益所有人,法规"
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isOk())
                .andReturn();
        long documentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(post("/regulation-library/documents/" + documentId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("PUBLISHED")));

        MvcResult pageResult = mockMvc.perform(get("/regulation-library/documents/page?page=1&size=10&keyword=客户尽职调查")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode documents = objectMapper.readTree(pageResult.getResponse().getContentAsString()).path("data").path("list");
        assertFalse(documents.isEmpty(), "全文检索应命中文档正文或摘要");

        MvcResult updateResult = mockMvc.perform(post("/regulation-library/documents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "docCode": "AML-KB-IT-UPDATE",
                                  "title": "集成测试监管动态",
                                  "docType": "REGULATORY_UPDATE",
                                  "categoryId": %d,
                                  "sourceType": "REGULATOR",
                                  "sourceOrg": "测试监管机构",
                                  "publishDate": "2026-05-15",
                                  "status": "PUBLISHED",
                                  "summary": "监管及行业动态查询样例",
                                  "content": "监管动态展示应与知识库检索共用资料底座。"
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isOk())
                .andReturn();
        long updateId = objectMapper.readTree(updateResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(get("/regulation-library/updates/page?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("集成测试监管动态")));

        mockMvc.perform(get("/regulation-library/documents/" + documentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("viewCount")));

        mockMvc.perform(put("/regulation-library/categories/" + categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryCode": "IT-REG-CATEGORY",
                                  "categoryName": "集成测试法规资料",
                                  "categoryType": "REGULATION",
                                  "sortOrder": 99,
                                  "status": "ENABLED"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/regulation-library/documents/" + updateId + "/archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("ARCHIVED")));

        mockMvc.perform(get("/regulation-library/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("totalDocuments")));
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
