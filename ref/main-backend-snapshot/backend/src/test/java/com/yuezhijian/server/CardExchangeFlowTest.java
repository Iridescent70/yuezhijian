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
class CardExchangeFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void consumedCardCanExchangeAtOriginalUnitValueWithExactSupplement() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600006001");
        long cardId = purchaseBaseCard(session, memberId, "exchange-sale-1");
        consumeOnce(session, memberId, cardId, "exchange-consume-bill-1", "exchange-consume-settle-1");
        long targetTypeId = createTargetCardType(session, "EXCHANGE_TARGET_12");

        JsonNode quote = json(postJson(session, "/api/v1/member-cards/" + cardId + "/exchange/quote", """
                {"targetCardTypeId":%d}
                """.formatted(targetTypeId), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(quote.path("oldRemainingTimes").decimalValue())
                .isEqualByComparingTo("9.0000");
        org.assertj.core.api.Assertions.assertThat(quote.path("oldRemainingValue").decimalValue())
                .isEqualByComparingTo("1152.0000");
        org.assertj.core.api.Assertions.assertThat(quote.path("differenceAmount").decimalValue())
                .isEqualByComparingTo("1528.0000");

        String body = """
                {"quoteNo":"%s","storeId":2,"employeeId":101,
                 "payments":[{"paymentMethodId":1,"amount":1528}],
                 "idempotencyKey":"exchange-execute-1"}
                """.formatted(quote.path("quoteNo").asText());
        JsonNode first = json(postJson(session, "/api/v1/member-cards/" + cardId + "/exchange", body, 200))
                .path("data");
        JsonNode repeated = json(postJson(session, "/api/v1/member-cards/" + cardId + "/exchange", body, 200))
                .path("data");
        org.assertj.core.api.Assertions.assertThat(repeated.path("exchangeId").asLong())
                .isEqualTo(first.path("exchangeId").asLong());
        long newCardId = first.path("newCard").path("id").asLong();

        mockMvc.perform(get("/api/v1/member-cards/{id}", cardId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.card.status").value("EXCHANGED"))
                .andExpect(jsonPath("$.data.card.remainingTimes").value(0))
                .andExpect(jsonPath("$.data.ledgers", hasSize(3)))
                .andExpect(jsonPath("$.data.ledgers[0].transactionType").value("EXCHANGE_OUT"));
        mockMvc.perform(get("/api/v1/member-cards/{id}", newCardId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.card.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.card.remainingTimes").value(12))
                .andExpect(jsonPath("$.data.ledgers[0].transactionType").value("EXCHANGE_IN"));
        mockMvc.perform(get("/api/v1/members/{id}/cards", memberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void exchangeRejectsWrongPaymentTotalAndStaleQuoteWithoutChangingCard() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600006002");
        long cardId = purchaseBaseCard(session, memberId, "exchange-sale-2");
        long targetTypeId = createTargetCardType(session, "EXCHANGE_TARGET_STALE");
        JsonNode quote = json(postJson(session, "/api/v1/member-cards/" + cardId + "/exchange/quote", """
                {"targetCardTypeId":%d}
                """.formatted(targetTypeId), 200)).path("data");

        mockMvc.perform(post("/api/v1/member-cards/{id}/exchange", cardId).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quoteNo":"%s","storeId":2,"employeeId":101,
                                 "payments":[{"paymentMethodId":1,"amount":1}],
                                 "idempotencyKey":"exchange-wrong-total"}
                                """.formatted(quote.path("quoteNo").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("补差支付合计必须等于换卡补差金额"));

        consumeOnce(session, memberId, cardId, "exchange-stale-bill-1", "exchange-stale-settle-1");
        mockMvc.perform(post("/api/v1/member-cards/{id}/exchange", cardId).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quoteNo":"%s","storeId":2,"employeeId":101,
                                 "payments":[{"paymentMethodId":1,"amount":1400}],
                                 "idempotencyKey":"exchange-stale-execute"}
                                """.formatted(quote.path("quoteNo").asText())))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/member-cards/{id}", cardId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.card.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.card.remainingTimes").value(9));
    }

    private long createTargetCardType(MockHttpSession session, String code) throws Exception {
        return json(postJson(session, "/api/v1/card-types", """
                {"code":"%s","name":"升级款式美甲12次卡","salePrice":2680,"listPrice":3576,
                 "totalTimes":12,"validDays":365,"purchaseThreshold":0,"autoRemindDays":30,
                 "storeIds":[2],"serviceRules":[
                   {"serviceId":302,"includedTimes":12,"deductTimes":1,"priority":10}
                 ]}
                """.formatted(code), 201)).path("data").path("id").asLong();
    }

    private void consumeOnce(
            MockHttpSession session, long memberId, long cardId, String billKey, String settleKey) throws Exception {
        JsonNode created = json(postJson(session, "/api/v1/bills", """
                {"memberId":%d,"storeId":2,"sourceType":"PC","personCount":1,"idempotencyKey":"%s"}
                """.formatted(memberId, billKey), 201)).path("data");
        JsonNode bill = json(postJson(session, "/api/v1/bills/" + created.path("id").asLong() + "/lines", """
                {"serviceId":301,"quantity":1,"employeeId":101,"version":"%s"}
                """.formatted(created.path("version").asText()), 200)).path("data");
        long billId = bill.path("bill").path("id").asLong();
        long lineId = bill.path("lines").get(0).path("id").asLong();
        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"payments":[],"cards":[{"billLineId":%d,"memberCardId":%d}]}
                """.formatted(lineId, cardId), 200)).path("data");
        postJson(session, "/api/v1/bills/" + billId + "/settle", """
                {"quoteNo":"%s","idempotencyKey":"%s"}
                """.formatted(quote.path("quoteNo").asText(), settleKey), 200);
    }

    private long purchaseBaseCard(MockHttpSession session, long memberId, String key) throws Exception {
        return json(postJson(session, "/api/v1/members/" + memberId + "/cards", """
                {"cardTypeId":501,"quantity":1,"storeId":2,"paymentMethodId":1,
                 "salesEmployeeId":101,"idempotencyKey":"%s"}
                """.formatted(key), 201)).path("data").path("cards").get(0).path("id").asLong();
    }

    private long createMember(MockHttpSession session, String mobile) throws Exception {
        return json(postJson(session, "/api/v1/members", """
                {"fullName":"换卡测试会员","mobile":"%s","joinStoreId":2,"ownerStoreId":2}
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
