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
class CardRefundFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void refundRepricesConsumedServiceAtOriginalAmountAndExecutesAfterApproval() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600008001");
        long cardId = purchaseBaseCard(session, memberId, "refund-sale-1");
        consumeDiscountedOnce(session, memberId, cardId, "refund-bill-1", "refund-settle-1");

        JsonNode quote = quote(session, cardId, 12);
        org.assertj.core.api.Assertions.assertThat(quote.path("originalAmount").decimalValue())
                .isEqualByComparingTo("1280.0000");
        org.assertj.core.api.Assertions.assertThat(quote.path("consumedRepriceAmount").decimalValue())
                .isEqualByComparingTo("168.0000");
        org.assertj.core.api.Assertions.assertThat(quote.path("refundAmount").decimalValue())
                .isEqualByComparingTo("1100.0000");
        org.assertj.core.api.Assertions.assertThat(quote.path("items")).hasSize(1);

        String submitBody = """
                {"quoteNo":"%s","refundMethodId":1,"storeId":2,"employeeId":101,
                 "reason":"会员申请退卡","idempotencyKey":"refund-request-1"}
                """.formatted(quote.path("quoteNo").asText());
        JsonNode submitted = json(postJson(
                session, "/api/v1/member-cards/" + cardId + "/refund-requests", submitBody, 201)).path("data");
        JsonNode repeated = json(postJson(
                session, "/api/v1/member-cards/" + cardId + "/refund-requests", submitBody, 201)).path("data");
        long requestId = submitted.path("request").path("id").asLong();
        org.assertj.core.api.Assertions.assertThat(repeated.path("request").path("id").asLong()).isEqualTo(requestId);
        mockMvc.perform(get("/api/v1/member-cards/{id}", cardId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.card.status").value("FROZEN"));

        JsonNode approved = json(postJson(session, "/api/v1/card-refund-requests/" + requestId + "/review", """
                {"approved":true,"comment":"金额核对无误","version":"%s"}
                """.formatted(submitted.path("request").path("version").asText()), 200)).path("data");
        String executeBody = """
                {"version":"%s","idempotencyKey":"refund-execute-1"}
                """.formatted(approved.path("request").path("version").asText());
        JsonNode executed = json(postJson(
                session, "/api/v1/card-refund-requests/" + requestId + "/execute", executeBody, 200)).path("data");
        JsonNode executedAgain = json(postJson(
                session, "/api/v1/card-refund-requests/" + requestId + "/execute", executeBody, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(executedAgain.path("request").path("id").asLong())
                .isEqualTo(requestId);
        org.assertj.core.api.Assertions.assertThat(executed.path("payment").path("amount").decimalValue())
                .isEqualByComparingTo("1100.0000");
        org.assertj.core.api.Assertions.assertThat(executed.path("request").path("commissionAdjustmentStatus").asText())
                .isEqualTo("PENDING_MODULE");
        mockMvc.perform(get("/api/v1/member-cards/{id}", cardId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.card.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.card.remainingTimes").value(0))
                .andExpect(jsonPath("$.data.ledgers", hasSize(3)))
                .andExpect(jsonPath("$.data.ledgers[0].transactionType").value("REFUND_OUT"));
    }

    @Test
    void rejectionRestoresCardAndElectronicRefundRequiresExternalReference() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600008002");
        long cardId = purchaseBaseCard(session, memberId, "refund-sale-2");
        JsonNode firstQuote = quote(session, cardId, 0);
        JsonNode submitted = submit(session, cardId, firstQuote, 1, "refund-request-reject");
        long firstRequestId = submitted.path("request").path("id").asLong();
        json(postJson(session, "/api/v1/card-refund-requests/" + firstRequestId + "/review", """
                {"approved":false,"comment":"资料不完整","version":"%s"}
                """.formatted(submitted.path("request").path("version").asText()), 200));
        mockMvc.perform(get("/api/v1/member-cards/{id}", cardId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.card.status").value("ACTIVE"));

        JsonNode secondQuote = quote(session, cardId, 0);
        JsonNode second = submit(session, cardId, secondQuote, 3, "refund-request-electronic");
        long secondRequestId = second.path("request").path("id").asLong();
        JsonNode approved = json(postJson(session, "/api/v1/card-refund-requests/" + secondRequestId + "/review", """
                {"approved":true,"version":"%s"}
                """.formatted(second.path("request").path("version").asText()), 200)).path("data");
        mockMvc.perform(post("/api/v1/card-refund-requests/{id}/execute", secondRequestId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":"%s","idempotencyKey":"refund-missing-reference"}
                                """.formatted(approved.path("request").path("version").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("微信支付必须填写外部退款凭证号"));
        mockMvc.perform(post("/api/v1/card-refund-requests/{id}/execute", secondRequestId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":"%s","externalRefundReference":"WX-REFUND-001",
                                 "idempotencyKey":"refund-electronic-execute"}
                                """.formatted(approved.path("request").path("version").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.externalRefundReference").value("WX-REFUND-001"));
    }

    private JsonNode quote(MockHttpSession session, long cardId, int fee) throws Exception {
        return json(postJson(session, "/api/v1/member-cards/" + cardId + "/refund-requests/quote", """
                {"feeAmount":%d}
                """.formatted(fee), 200)).path("data");
    }

    private JsonNode submit(
            MockHttpSession session, long cardId, JsonNode quote, long methodId, String key) throws Exception {
        return json(postJson(session, "/api/v1/member-cards/" + cardId + "/refund-requests", """
                {"quoteNo":"%s","refundMethodId":%d,"storeId":2,"employeeId":101,
                 "reason":"会员确认退卡","idempotencyKey":"%s"}
                """.formatted(quote.path("quoteNo").asText(), methodId, key), 201)).path("data");
    }

    private void consumeDiscountedOnce(
            MockHttpSession session, long memberId, long cardId, String billKey, String settleKey) throws Exception {
        JsonNode created = json(postJson(session, "/api/v1/bills", """
                {"memberId":%d,"storeId":2,"sourceType":"PC","personCount":1,"idempotencyKey":"%s"}
                """.formatted(memberId, billKey), 201)).path("data");
        JsonNode bill = json(postJson(session, "/api/v1/bills/" + created.path("id").asLong() + "/lines", """
                {"serviceId":301,"quantity":1,"employeeId":101,"version":"%s"}
                """.formatted(created.path("version").asText()), 200)).path("data");
        long billId = bill.path("bill").path("id").asLong();
        JsonNode discounted = json(postJson(session, "/api/v1/bills/" + billId + "/discounts", """
                {"discountType":"RATE","value":0.5,"reason":"测试五折","version":"%s"}
                """.formatted(bill.path("bill").path("version").asText()), 200)).path("data");
        long lineId = discounted.path("lines").get(0).path("id").asLong();
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
                {"fullName":"退卡测试会员","mobile":"%s","joinStoreId":2,"ownerStoreId":2}
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
