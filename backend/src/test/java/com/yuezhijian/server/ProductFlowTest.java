package com.yuezhijian.server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ProductFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void productCanBeCreatedEditedAndPutOffSale() throws Exception {
        MockHttpSession session = login();
        mockMvc.perform(get("/api/v1/item-categories?type=PRODUCT").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("RETAIL_PRODUCT"));
        mockMvc.perform(get("/api/v1/units").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("TIME"));

        String created = mockMvc.perform(post("/api/v1/products").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "code":"PRD-TEST-901","name":"自动化产品","categoryId":2,"unitId":2,
                                  "barcode":"690000009901","costPrice":20,"salePrice":88,"storePrice":78,
                                  "trackStock":true,"storeIds":[2],"description":"初始产品"
                                }
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").path("id").asLong();
        mockMvc.perform(get("/api/v1/products?storeId=2&keyword=690000009901").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].storePrice").value(78));

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/v1/products/{id}", id).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        String update = """
                {
                  "name":"已编辑产品","categoryId":2,"unitId":2,"barcode":"690000009901",
                  "costPrice":22,"salePrice":90,"trackStock":true,"description":"更新产品",
                  "status":"ACTIVE","storeId":2,"storePrice":80,"saleStatus":"OFF_SALE",
                  "version":"%s"
                }
                """.formatted(detail.path("version").asText());
        mockMvc.perform(put("/api/v1/products/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("已编辑产品"))
                .andExpect(jsonPath("$.data.stores[0].saleStatus").value("OFF_SALE"));
        mockMvc.perform(put("/api/v1/products/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict());
    }

    @Test
    void productRejectsInvalidReferencesAndDuplicateCodes() throws Exception {
        MockHttpSession session = login();
        String request = """
                {
                  "code":"PRD-DUP-902","name":"测试产品","categoryId":2,"unitId":2,
                  "costPrice":20,"salePrice":88,"storePrice":78,"trackStock":true,"storeIds":[2]
                }
                """;
        mockMvc.perform(post("/api/v1/products").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/products").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/products").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(request.replace("\"categoryId\":2", "\"categoryId\":999")))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"TestPassword!2026\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
