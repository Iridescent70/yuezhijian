package com.yuezhijian.server;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
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
class CardTransferFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void consumedCardCanTransferAllRemainingAssetsAndRepeatIdempotently() throws Exception {
        MockHttpSession session = login();
        long sourceMemberId = createMember(session, "13600007001", "转出会员");
        long recipientMemberId = createMember(session, "13600007002", "接收会员");
        long cardId = purchaseBaseCard(session, sourceMemberId, "transfer-sale-1");
        consumeOnce(session, sourceMemberId, cardId, "transfer-bill-1", "transfer-settle-1");
        JsonNode source = card(session, cardId).path("card");
        String expiresAt = LocalDateTime.now().plusDays(200).withNano(0).toString();
        String body = """
                {"recipientMemberId":%d,"expiresAt":"%s","storeId":2,"employeeId":101,
                 "reason":"会员本人确认转赠","sourceCardVersion":"%s",
                 "idempotencyKey":"transfer-execute-1"}
                """.formatted(recipientMemberId, expiresAt, source.path("version").asText());

        JsonNode first = json(postJson(session, "/api/v1/member-cards/" + cardId + "/transfer", body, 200))
                .path("data");
        JsonNode repeated = json(postJson(session, "/api/v1/member-cards/" + cardId + "/transfer", body, 200))
                .path("data");
        org.assertj.core.api.Assertions.assertThat(repeated.path("transferId").asLong())
                .isEqualTo(first.path("transferId").asLong());
        org.assertj.core.api.Assertions.assertThat(first.path("remainingTimes").decimalValue())
                .isEqualByComparingTo("9.0000");
        org.assertj.core.api.Assertions.assertThat(first.path("remainingValue").decimalValue())
                .isEqualByComparingTo("1152.0000");
        long targetCardId = first.path("targetCard").path("id").asLong();

        mockMvc.perform(get("/api/v1/member-cards/{id}", cardId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.card.status").value("TRANSFERRED"))
                .andExpect(jsonPath("$.data.card.remainingTimes").value(0))
                .andExpect(jsonPath("$.data.ledgers", hasSize(3)))
                .andExpect(jsonPath("$.data.ledgers[0].transactionType").value("TRANSFER_OUT"));
        mockMvc.perform(get("/api/v1/member-cards/{id}", targetCardId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.card.memberId").value(recipientMemberId))
                .andExpect(jsonPath("$.data.card.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.card.totalTimes").value(9))
                .andExpect(jsonPath("$.data.card.remainingTimes").value(9))
                .andExpect(jsonPath("$.data.card.purchasePrice").value(1152))
                .andExpect(jsonPath("$.data.ledgers[0].transactionType").value("TRANSFER_IN"));
        mockMvc.perform(get("/api/v1/members/{id}/cards", recipientMemberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void transferRejectsSelfRecipientAndStaleCardVersion() throws Exception {
        MockHttpSession session = login();
        long sourceMemberId = createMember(session, "13600007003", "版本测试会员");
        long recipientMemberId = createMember(session, "13600007004", "版本接收会员");
        long cardId = purchaseBaseCard(session, sourceMemberId, "transfer-sale-2");
        String oldVersion = card(session, cardId).path("card").path("version").asText();
        String expiresAt = LocalDateTime.now().plusDays(100).withNano(0).toString();

        mockMvc.perform(post("/api/v1/member-cards/{id}/transfer", cardId).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"recipientMemberId":%d,"expiresAt":"%s","storeId":2,
                                 "reason":"不能转给自己","sourceCardVersion":"%s",
                                 "idempotencyKey":"transfer-self"}
                                """.formatted(sourceMemberId, expiresAt, oldVersion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("次卡不能转赠给原持卡会员"));

        consumeOnce(session, sourceMemberId, cardId, "transfer-bill-2", "transfer-settle-2");
        mockMvc.perform(post("/api/v1/member-cards/{id}/transfer", cardId).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"recipientMemberId":%d,"expiresAt":"%s","storeId":2,
                                 "reason":"使用过期页面版本","sourceCardVersion":"%s",
                                 "idempotencyKey":"transfer-stale"}
                                """.formatted(recipientMemberId, expiresAt, oldVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("原次卡状态已发生变化，请刷新后重试"));
        mockMvc.perform(get("/api/v1/members/{id}/cards", recipientMemberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
    }

    private JsonNode card(MockHttpSession session, long cardId) throws Exception {
        return json(mockMvc.perform(get("/api/v1/member-cards/{id}", cardId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
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

    private long createMember(MockHttpSession session, String mobile, String name) throws Exception {
        return json(postJson(session, "/api/v1/members", """
                {"fullName":"%s","mobile":"%s","joinStoreId":2,"ownerStoreId":2}
                """.formatted(name, mobile), 201)).path("data").path("memberId").asLong();
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
