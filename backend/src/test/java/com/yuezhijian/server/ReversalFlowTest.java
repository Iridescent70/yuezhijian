package com.yuezhijian.server;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
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
class ReversalFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void approvedFullReversalRestoresBalancePointsAndPaymentIdempotently() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600005001");
        rechargeAndConfirm(session, memberId, 100, "reversal-recharge-1");
        postJson(session, "/api/v1/members/" + memberId + "/points/adjustments", """
                {"changePoints":1000,"reason":"冲销测试积分","idempotencyKey":"reversal-points-1"}
                """, 200);
        JsonNode bill = createBillWithLine(session, memberId, 302, "reversal-bill-1");
        long billId = bill.path("bill").path("id").asLong();
        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"balanceAmount":100,"points":1000,
                 "payments":[{"paymentMethodId":1,"amount":188}]}
                """, 200)).path("data");
        postJson(session, "/api/v1/bills/" + billId + "/settle", """
                {"quoteNo":"%s","idempotencyKey":"reversal-settle-1"}
                """.formatted(quote.path("quoteNo").asText()), 200);

        JsonNode submitted = json(postJson(session, "/api/v1/bills/" + billId + "/reversals", """
                {"reason":"客户取消整笔消费","idempotencyKey":"reversal-request-1"}
                """, 201)).path("data");
        long reversalId = submitted.path("reversal").path("id").asLong();
        mockMvc.perform(post("/api/v1/reversals/{id}/execute", reversalId).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":"%s","idempotencyKey":"reversal-execute-too-early"}
                                """.formatted(submitted.path("reversal").path("version").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("冲销申请审批通过后才能执行"));

        JsonNode approved = json(postJson(session, "/api/v1/reversals/" + reversalId + "/review", """
                {"approved":true,"comment":"金额核对无误","version":"%s"}
                """.formatted(submitted.path("reversal").path("version").asText()), 200)).path("data");
        mockMvc.perform(post("/api/v1/reversals/{id}/execute", reversalId).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":"%s","idempotencyKey":"reversal-execute-stale"}
                                """.formatted(submitted.path("reversal").path("version").asText())))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/members/{id}/balance-account", memberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availableBalance").value(0));
        mockMvc.perform(get("/api/v1/members/{id}/point-account", memberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availablePoints").value(0));
        String executeBody = """
                {"version":"%s","idempotencyKey":"reversal-execute-1"}
                """.formatted(approved.path("reversal").path("version").asText());
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/reversals/{id}/execute", reversalId).with(csrf()).session(session)
                            .contentType(MediaType.APPLICATION_JSON).content(executeBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reversal.status").value("EXECUTED"))
                    .andExpect(jsonPath("$.data.payments[0].status").value("REFUNDED"));
        }

        mockMvc.perform(get("/api/v1/bills/{id}", billId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bill.status").value("REVERSED"))
                .andExpect(jsonPath("$.data.bill.receivedAmount").value(298))
                .andExpect(jsonPath("$.data.payments[0].status").value("REFUNDED"));
        mockMvc.perform(get("/api/v1/members/{id}/balance-account", memberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availableBalance").value(100));
        mockMvc.perform(get("/api/v1/members/{id}/point-account", memberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availablePoints").value(1000));
        mockMvc.perform(get("/api/v1/members/{id}/balance-ledgers", memberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].transactionType").value("REFUND"));
        mockMvc.perform(get("/api/v1/members/{id}/point-ledgers", memberId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].transactionType").value("REFUND"));
        mockMvc.perform(get("/api/v1/reversals").param("status", "executed").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].reversalNo", hasItem(
                        submitted.path("reversal").path("reversalNo").asText())));
    }

    @Test
    void cardOnlyReversalRestoresTimesAndWritesRefundLedger() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600005002");
        JsonNode sale = json(postJson(session, "/api/v1/members/" + memberId + "/cards", """
                {"cardTypeId":501,"quantity":1,"storeId":2,"paymentMethodId":1,
                 "salesEmployeeId":101,"idempotencyKey":"reversal-card-sale-1"}
                """, 201)).path("data");
        long cardId = sale.path("cards").get(0).path("id").asLong();
        JsonNode bill = createBillWithLine(session, memberId, 301, "reversal-card-bill-1");
        long billId = bill.path("bill").path("id").asLong();
        long billLineId = bill.path("lines").get(0).path("id").asLong();
        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"payments":[],"cards":[{"billLineId":%d,"memberCardId":%d}]}
                """.formatted(billLineId, cardId), 200)).path("data");
        postJson(session, "/api/v1/bills/" + billId + "/settle", """
                {"quoteNo":"%s","idempotencyKey":"reversal-card-settle-1"}
                """.formatted(quote.path("quoteNo").asText()), 200);
        JsonNode submitted = json(postJson(session, "/api/v1/bills/" + billId + "/reversals", """
                {"reason":"次卡消费撤销","idempotencyKey":"reversal-card-request-1"}
                """, 201)).path("data");
        long reversalId = submitted.path("reversal").path("id").asLong();
        JsonNode approved = json(postJson(session, "/api/v1/reversals/" + reversalId + "/review", """
                {"approved":true,"version":"%s"}
                """.formatted(submitted.path("reversal").path("version").asText()), 200)).path("data");
        postJson(session, "/api/v1/reversals/" + reversalId + "/execute", """
                {"version":"%s","idempotencyKey":"reversal-card-execute-1"}
                """.formatted(approved.path("reversal").path("version").asText()), 200);

        mockMvc.perform(get("/api/v1/member-cards/{id}", cardId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balances[0].remainingTimes").value(10))
                .andExpect(jsonPath("$.data.ledgers", hasSize(3)))
                .andExpect(jsonPath("$.data.ledgers[0].transactionType").value("REFUND"));
    }

    @Test
    void cashChangeIsNotCountedAsRefundAndOriginalAmountsRemainAuditable() throws Exception {
        MockHttpSession session = login();
        JsonNode bill = createBillWithLine(session, 1001, 301, "reversal-change-bill-1");
        long billId = bill.path("bill").path("id").asLong();
        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"payments":[{"paymentMethodId":1,"amount":200}]}
                """, 200)).path("data");
        postJson(session, "/api/v1/bills/" + billId + "/settle", """
                {"quoteNo":"%s","idempotencyKey":"reversal-change-settle-1"}
                """.formatted(quote.path("quoteNo").asText()), 200);
        JsonNode submitted = json(postJson(session, "/api/v1/bills/" + billId + "/reversals", """
                {"reason":"现金账单整单撤销","idempotencyKey":"reversal-change-request-1"}
                """, 201)).path("data");

        org.assertj.core.api.Assertions.assertThat(submitted.path("payments").get(0).path("amount").decimalValue())
                .isEqualByComparingTo("168.0000");
        long reversalId = submitted.path("reversal").path("id").asLong();
        JsonNode approved = json(postJson(session, "/api/v1/reversals/" + reversalId + "/review", """
                {"approved":true,"version":"%s"}
                """.formatted(submitted.path("reversal").path("version").asText()), 200)).path("data");
        postJson(session, "/api/v1/reversals/" + reversalId + "/execute", """
                {"version":"%s","idempotencyKey":"reversal-change-execute-1"}
                """.formatted(approved.path("reversal").path("version").asText()), 200);

        mockMvc.perform(get("/api/v1/bills/{id}", billId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bill.status").value("REVERSED"))
                .andExpect(jsonPath("$.data.bill.receivedAmount").value(168))
                .andExpect(jsonPath("$.data.bill.changeAmount").value(32));
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
                {"fullName":"冲销测试会员","mobile":"%s","joinStoreId":2,"ownerStoreId":2}
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
