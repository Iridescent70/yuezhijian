package com.yuezhijian.server;

import static org.hamcrest.Matchers.hasSize;
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
class AssetFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void rechargeConfirmationCreditsPaidAndGiftAmountsExactlyOnce() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600002001");
        JsonNode quote = json(postJson(session, "/api/v1/members/" + memberId + "/recharges/quote", """
                {"rechargeAmount":500,"giftAmount":50,"paymentMethodId":1}
                """, 200)).path("data");
        JsonNode order = json(postJson(session, "/api/v1/members/" + memberId + "/recharges", """
                {"quoteNo":"%s","storeId":2,"idempotencyKey":"asset-test-recharge-1"}
                """.formatted(quote.path("quoteNo").asText()), 201)).path("data");

        mockMvc.perform(post("/api/v1/recharges/{id}/confirm", order.path("id").asLong())
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"%s\"}".formatted(order.path("version").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/members/{id}/balance-account", memberId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalance").value(550))
                .andExpect(jsonPath("$.data.totalRecharged").value(500));
        mockMvc.perform(get("/api/v1/members/{id}/balance-ledgers", memberId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].afterBalance").value(550));

        mockMvc.perform(post("/api/v1/recharges/{id}/confirm", order.path("id").asLong())
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"%s\"}".formatted(order.path("version").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        mockMvc.perform(get("/api/v1/members/{id}/balance-account", memberId).session(session))
                .andExpect(jsonPath("$.data.availableBalance").value(550));
    }

    @Test
    void electronicRechargeRequiresExternalReference() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600002002");
        JsonNode quote = json(postJson(session, "/api/v1/members/" + memberId + "/recharges/quote", """
                {"rechargeAmount":100,"giftAmount":0,"paymentMethodId":3}
                """, 200)).path("data");

        mockMvc.perform(post("/api/v1/members/{id}/recharges", memberId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quoteNo":"%s","storeId":2,"idempotencyKey":"asset-test-recharge-2"}
                                """.formatted(quote.path("quoteNo").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("微信支付必须填写外部凭证号"));
    }

    @Test
    void pointAdjustmentIsIdempotentAndCannotOverdraw() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600002003");
        String adjustment = """
                {"changePoints":100,"reason":"开业赠送","idempotencyKey":"asset-test-point-1"}
                """;
        postJson(session, "/api/v1/members/" + memberId + "/points/adjustments", adjustment, 200);
        postJson(session, "/api/v1/members/" + memberId + "/points/adjustments", adjustment, 200);

        mockMvc.perform(get("/api/v1/members/{id}/point-account", memberId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availablePoints").value(100))
                .andExpect(jsonPath("$.data.lifetimePoints").value(100));
        mockMvc.perform(get("/api/v1/members/{id}/point-ledgers", memberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(post("/api/v1/members/{id}/points/adjustments", memberId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changePoints":-101,"reason":"错误扣减","idempotencyKey":"asset-test-point-2"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("可用积分不足"));
    }

    private long createMember(MockHttpSession session, String mobile) throws Exception {
        String response = postJson(session, "/api/v1/members", """
                {"fullName":"资产测试会员","mobile":"%s","joinStoreId":2,"ownerStoreId":2}
                """.formatted(mobile), 201);
        return json(response).path("data").path("memberId").asLong();
    }

    private String postJson(MockHttpSession session, String url, String content, int statusCode) throws Exception {
        return mockMvc.perform(post(url).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().is(statusCode)).andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String value) throws Exception { return objectMapper.readTree(value); }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"TestPassword!2026\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
