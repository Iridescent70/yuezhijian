package com.yuezhijian.server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
class PaymentMethodFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void paymentDefinitionStoreAvailabilityAndOrderingCanBeManaged() throws Exception {
        MockHttpSession session = login();
        mockMvc.perform(get("/api/v1/payment-methods/management").session(session).param("storeId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stores[0].storeId").value(2));

        JsonNode created = data(postJson(session, "/api/v1/payment-methods", """
                {
                  "code":"TEST_CHANNEL_946","name":"自动化测试支付","type":"OTHER",
                  "electronic":true,"includedInRevenue":false,"needsExternalReference":true,
                  "status":"ACTIVE","storeIds":[2]
                }
                """));
        long id = created.path("id").asLong();
        String initialVersion = created.path("version").asText();

        mockMvc.perform(get("/api/v1/payment-methods").session(session).param("storeId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].name".formatted(id))
                        .value(org.hamcrest.Matchers.hasItem("自动化测试支付")));

        JsonNode updated = data(putJson(session, "/api/v1/payment-methods/" + id, """
                {
                  "name":"自动化支付已编辑","type":"OTHER","electronic":true,
                  "includedInRevenue":true,"needsExternalReference":false,
                  "status":"ACTIVE","version":"%s"
                }
                """.formatted(initialVersion)));
        mockMvc.perform(put("/api/v1/payment-methods/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "name":"过期更新","type":"OTHER","electronic":true,
                                  "includedInRevenue":true,"needsExternalReference":false,
                                  "status":"ACTIVE","version":"%s"
                                }
                                """.formatted(initialVersion)))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(updated.path("name").asText()).isEqualTo("自动化支付已编辑");

        JsonNode storeOne = data(putJson(
                session, "/api/v1/payment-methods/%d/stores/1".formatted(id),
                "{\"applicable\":true,\"enabled\":false,\"sortNo\":60}"));
        String storeVersion = storeOne.path("stores").path(0).path("version").asText();
        mockMvc.perform(get("/api/v1/payment-methods").session(session).param("storeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(id)).isEmpty());
        data(putJson(
                session, "/api/v1/payment-methods/%d/stores/1".formatted(id),
                "{\"applicable\":true,\"enabled\":true,\"sortNo\":60,\"version\":\"%s\"}"
                        .formatted(storeVersion)));
        mockMvc.perform(get("/api/v1/payment-methods").session(session).param("storeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(id)).isNotEmpty());

        JsonNode storeTwoMethods = data(mockMvc.perform(
                        get("/api/v1/payment-methods/management").session(session).param("storeId", "2"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ObjectNode sortRequest = objectMapper.createObjectNode().put("storeId", 2);
        ArrayNode sortItems = sortRequest.putArray("items");
        int sortNo = storeTwoMethods.size() * 10;
        for (JsonNode method : storeTwoMethods) {
            JsonNode store = method.path("stores").path(0);
            if (!store.path("applicable").asBoolean()) continue;
            sortItems.addObject()
                    .put("paymentMethodId", method.path("id").asLong())
                    .put("sortNo", sortNo)
                    .put("version", store.path("version").asText());
            sortNo -= 10;
        }
        mockMvc.perform(put("/api/v1/payment-methods/sort").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(sortRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id));

        mockMvc.perform(get("/api/v1/audit-logs").session(session)
                        .param("objectType", "PAYMENT_METHOD").param("objectId", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    private String postJson(MockHttpSession session, String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String putJson(MockHttpSession session, String path, String body) throws Exception {
        return mockMvc.perform(put(path).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode data(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        org.assertj.core.api.Assertions.assertThat(root.path("code").asText()).isEqualTo("0");
        return root.path("data");
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"TestPassword!2026\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
