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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 机构建档、治理信息维护、提交和审批的接口闭环测试。
 */
@DisplayName("机构治理集成测试")
class OrganizationGovernanceIntegrationTest extends BaseIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("机构首次登记全流程可贯通")
    void initialRegistrationFlow() throws Exception {
        String token = login();
        String suffix = String.valueOf(System.currentTimeMillis()).substring(5);
        String orgCode = "ORG-IT-" + suffix;
        String creditCode = "91310000IT" + suffix;

        MvcResult createOrgResult = authorizedPost(token, "/organizations", """
                {
                  "orgCode": "%s",
                  "orgName": "华岳保险股份有限公司%s",
                  "unifiedCreditCode": "%s",
                  "leiCode": "300300AML%s",
                  "orgType": "HEAD_OFFICE",
                  "registeredAddress": "上海市浦东新区世纪大道100号",
                  "businessAddress": "上海市浦东新区世纪大道100号",
                  "legalRepresentative": "周明远",
                  "registeredCapital": 500000,
                  "businessScope": "人寿保险、健康保险及经批准的保险资金运用业务",
                  "regulatorName": "国家金融监督管理总局上海监管局"
                }
                """.formatted(orgCode, suffix, creditCode, suffix));
        JsonNode organization = responseData(createOrgResult);
        long organizationId = organization.path("id").asLong();
        assertTrue(organizationId > 0);
        assertEquals("DRAFT", organization.path("registrationStatus").asText());

        authorizedPost(token, "/organizations/" + organizationId + "/persons", """
                {
                  "personType":"AML_OFFICER",
                  "personName":"顾清和",
                  "title":"反洗钱合规负责人",
                  "department":"法律合规部",
                  "phone":"021-68001234",
                  "email":"aml.officer@example.test",
                  "primaryFlag":true,
                  "status":"ENABLED"
                }
                """);
        authorizedPost(token, "/organizations/" + organizationId + "/persons", """
                {
                  "personType":"CONTACT",
                  "personName":"沈安宁",
                  "title":"监管联络专员",
                  "department":"法律合规部",
                  "phone":"021-68005678",
                  "email":"aml.contact@example.test",
                  "primaryFlag":true,
                  "status":"ENABLED"
                }
                """);
        authorizedPost(token, "/organizations/" + organizationId + "/shareholders", """
                {
                  "shareholderName":"华岳金融控股有限公司",
                  "shareholderType":"ORGANIZATION",
                  "registrationCode":"91310000MA1HOLDING",
                  "ownershipPercentage":65.0000,
                  "controllingFlag":true,
                  "status":"ENABLED"
                }
                """);

        MvcResult createRegistration = authorizedPost(token,
                "/organizations/" + organizationId + "/registrations", "{\"commitmentAccepted\":true}");
        long registrationId = responseData(createRegistration).path("id").asLong();
        assertTrue(registrationId > 0);

        MvcResult submitResult = authorizedPost(token,
                "/organizations/registrations/" + registrationId + "/submit", null);
        JsonNode submitted = responseData(submitResult);
        assertEquals("PENDING_REVIEW", submitted.path("status").asText());
        assertFalse(submitted.path("snapshotJson").asText().isBlank(), "提交时应固化机构档案快照");

        MvcResult reviewResult = authorizedPost(token,
                "/organizations/registrations/" + registrationId + "/review",
                "{\"approved\":true,\"opinion\":\"机构信息完整，同意登记\"}");
        assertEquals("APPROVED", responseData(reviewResult).path("status").asText());

        MvcResult detailResult = mockMvc.perform(get("/organizations/" + organizationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode detail = responseData(detailResult);
        assertEquals("APPROVED", detail.path("organization").path("registrationStatus").asText());
        assertEquals("ENABLED", detail.path("organization").path("status").asText());
        assertEquals(2, detail.path("persons").size());
        assertEquals(1, detail.path("shareholders").size());
        assertTrue(detail.path("reviewLogs").size() >= 3);

        MvcResult pageResult = mockMvc.perform(get("/organizations/page")
                        .param("page", "1").param("size", "10").param("keyword", orgCode)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertEquals(1, responseData(pageResult).path("total").asInt());
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
