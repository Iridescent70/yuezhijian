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
class AssetSettlementFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void serviceCanBeSettledByCardAndRepeatedRequestDoesNotDeductAgain() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600004001");
        JsonNode sale = json(postJson(session, "/api/v1/members/" + memberId + "/cards", """
                {"cardTypeId":501,"quantity":1,"storeId":2,"paymentMethodId":1,
                 "salesEmployeeId":101,"idempotencyKey":"asset-settle-card-sale-1"}
                """, 201)).path("data");
        long cardId = sale.path("cards").get(0).path("id").asLong();
        JsonNode bill = createBillWithLine(session, memberId, 301, "asset-settle-card-bill-1");
        long billId = bill.path("bill").path("id").asLong();
        long billLineId = bill.path("lines").get(0).path("id").asLong();

        mockMvc.perform(get("/api/v1/bills/{id}/card-options", billId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].memberCardId").value(cardId))
                .andExpect(jsonPath("$.data[0].requiredTimes").value(1))
                .andExpect(jsonPath("$.data[0].recommended").value(true));
        mockMvc.perform(get("/api/v1/bills/{id}/asset-options", billId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsPerYuan").value(100))
                .andExpect(jsonPath("$.data.balanceAccount.availableBalance").value(0))
                .andExpect(jsonPath("$.data.cardOptions", hasSize(1)));

        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"payments":[],"cards":[{"billLineId":%d,"memberCardId":%d}]}
                """.formatted(billLineId, cardId), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(quote.path("assetAmount").decimalValue())
                .isEqualByComparingTo("168.0000");
        org.assertj.core.api.Assertions.assertThat(quote.path("externalPaymentAmount").decimalValue())
                .isEqualByComparingTo("0.0000");

        String settleBody = """
                {"quoteNo":"%s","idempotencyKey":"asset-settle-card-1"}
                """.formatted(quote.path("quoteNo").asText());
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/bills/{id}/settle", billId).with(csrf()).session(session)
                            .contentType(MediaType.APPLICATION_JSON).content(settleBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.bill.status").value("SETTLED"));
        }
        mockMvc.perform(get("/api/v1/member-cards/{id}", cardId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balances[0].remainingTimes").value(9))
                .andExpect(jsonPath("$.data.ledgers", hasSize(2)))
                .andExpect(jsonPath("$.data.ledgers[0].transactionType").value("CONSUME"));
    }

    @Test
    void balancePointsAndCashCanSettleOneBillTogether() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600004002");
        rechargeAndConfirm(session, memberId, 100, "asset-settle-recharge-1");
        postJson(session, "/api/v1/members/" + memberId + "/points/adjustments", """
                {"changePoints":1000,"reason":"组合结算测试","idempotencyKey":"asset-settle-points-1"}
                """, 200);
        JsonNode bill = createBillWithLine(session, memberId, 302, "asset-settle-mixed-bill-1");
        long billId = bill.path("bill").path("id").asLong();

        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"balanceAmount":100,"points":1000,
                 "payments":[{"paymentMethodId":1,"amount":188}]}
                """, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(quote.path("assetAmount").decimalValue())
                .isEqualByComparingTo("110.0000");
        org.assertj.core.api.Assertions.assertThat(quote.path("externalPaymentAmount").decimalValue())
                .isEqualByComparingTo("188.0000");
        org.assertj.core.api.Assertions.assertThat(quote.path("assets").size()).isEqualTo(2);

        postJson(session, "/api/v1/bills/" + billId + "/settle", """
                {"quoteNo":"%s","idempotencyKey":"asset-settle-mixed-1"}
                """.formatted(quote.path("quoteNo").asText()), 200);
        mockMvc.perform(get("/api/v1/members/{id}/balance-account", memberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availableBalance").value(0));
        mockMvc.perform(get("/api/v1/members/{id}/point-account", memberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availablePoints").value(0));
        mockMvc.perform(get("/api/v1/bills/{id}", billId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bill.status").value("SETTLED"))
                .andExpect(jsonPath("$.data.payments", hasSize(1)))
                .andExpect(jsonPath("$.data.assetUsages", hasSize(2)))
                .andExpect(jsonPath("$.data.payments[0].amount").value(188));
    }

    @Test
    void assetChangeInvalidatesOldQuoteWithoutDeductingAnything() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600004003");
        rechargeAndConfirm(session, memberId, 100, "asset-settle-stale-recharge-1");
        JsonNode bill = createBillWithLine(session, memberId, 301, "asset-settle-stale-bill-1");
        long billId = bill.path("bill").path("id").asLong();
        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"balanceAmount":100,"payments":[{"paymentMethodId":1,"amount":68}]}
                """, 200)).path("data");

        rechargeAndConfirm(session, memberId, 10, "asset-settle-stale-recharge-2");
        mockMvc.perform(post("/api/v1/bills/{id}/settle", billId).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quoteNo":"%s","idempotencyKey":"asset-settle-stale-1"}
                                """.formatted(quote.path("quoteNo").asText())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("储值余额已发生变化，请重新试算"));
        mockMvc.perform(get("/api/v1/members/{id}/balance-account", memberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availableBalance").value(110));
        mockMvc.perform(get("/api/v1/bills/{id}", billId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.status").value("PENDING_PAYMENT"));
    }

    private JsonNode createBillWithLine(
            MockHttpSession session, long memberId, long serviceId, String key) throws Exception {
        JsonNode created = json(postJson(session, "/api/v1/bills", """
                {"memberId":%d,"storeId":2,"sourceType":"PC","personCount":1,"idempotencyKey":"%s"}
                """.formatted(memberId, key), 201)).path("data");
        return json(postJson(session, "/api/v1/bills/" + created.path("id").asLong() + "/lines", """
                {"serviceId":%d,"quantity":1,"employeeId":101,"version":"%s"}
                """.formatted(serviceId, created.path("version").asText()), 200)).path("data");
    }

    private void rechargeAndConfirm(MockHttpSession session, long memberId, int amount, String key) throws Exception {
        JsonNode quote = json(postJson(session, "/api/v1/members/" + memberId + "/recharges/quote", """
                {"rechargeAmount":%d,"giftAmount":0,"paymentMethodId":1}
                """.formatted(amount), 200)).path("data");
        JsonNode order = json(postJson(session, "/api/v1/members/" + memberId + "/recharges", """
                {"quoteNo":"%s","storeId":2,"idempotencyKey":"%s"}
                """.formatted(quote.path("quoteNo").asText(), key), 201)).path("data");
        postJson(session, "/api/v1/recharges/" + order.path("id").asLong() + "/confirm", """
                {"version":"%s"}
                """.formatted(order.path("version").asText()), 200);
    }

    private long createMember(MockHttpSession session, String mobile) throws Exception {
        return json(postJson(session, "/api/v1/members", """
                {"fullName":"组合结算测试会员","mobile":"%s","joinStoreId":2,"ownerStoreId":2}
                """.formatted(mobile), 201)).path("data").path("memberId").asLong();
    }

    private String postJson(MockHttpSession session, String url, String content, int code) throws Exception {
        return mockMvc.perform(post(url).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().is(code)).andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String value) throws Exception { return objectMapper.readTree(value); }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"TestPassword!2026\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
