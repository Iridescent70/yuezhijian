package com.yuezhijian.server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.profiles.active=memory",
        "app.bootstrap.username=test-admin",
        "app.bootstrap.password=TestPassword!2026"
})
@AutoConfigureMockMvc
class AuditLogFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createdProductCanBeFoundAndInspectedFromAuditConsole() throws Exception {
        MockHttpSession session = login();
        String created = mockMvc.perform(post("/api/v1/products").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "code":"PRD-AUDIT-945","name":"审计查询样品","categoryId":2,"unitId":2,
                                  "barcode":"690000009945","costPrice":20,"salePrice":88,"storePrice":78,
                                  "trackStock":true,"storeIds":[2],"description":"验证系统审计查询"
                                }
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(created).path("data").path("id").asLong();

        String pageBody = mockMvc.perform(get("/api/v1/audit-logs").session(session)
                        .param("operator", "本地管理员")
                        .param("module", "CATALOG")
                        .param("action", "CREATE")
                        .param("objectType", "PRODUCT")
                        .param("objectId", String.valueOf(productId))
                        .param("result", "success")
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].operatorName").value("本地管理员"))
                .andExpect(jsonPath("$.data.items[0].objectId").value(String.valueOf(productId)))
                .andExpect(jsonPath("$.data.items[0].result").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        JsonNode row = objectMapper.readTree(pageBody).path("data").path("items").path(0);

        mockMvc.perform(get("/api/v1/audit-logs/{id}", row.path("id").asLong()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.traceId").value(row.path("traceId").asText()))
                .andExpect(jsonPath("$.data.afterValues.name").value("审计查询样品"))
                .andExpect(jsonPath("$.data.afterValues.salePrice").value("88"));

        mockMvc.perform(get("/api/v1/audit-logs").session(session)
                        .param("occurredFrom", "2026-08-01")
                        .param("occurredTo", "2026-07-30"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"TestPassword!2026\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
