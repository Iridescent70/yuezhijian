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
class VoucherFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void unboundVoucherCanBindSettleAndReturnOnFullReversal() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "13600009001");
        JsonNode definition = json(postJson(session, "/api/v1/vouchers", """
                {"code":"CASH50","name":"满100减50券","benefitType":"FIXED_AMOUNT",
                 "faceAmount":50,"discountRate":1,"minSpend":100,"validDays":30,
                 "commissionRule":"按券后实收计算"}
                """, 201)).path("data");
        JsonNode issued = json(postJson(session, "/api/v1/voucher-code-issues", """
                {"voucherId":%d,"count":1,"idempotencyKey":"voucher-issue-1"}
                """.formatted(definition.path("id").asLong()), 201)).path("data").get(0);
        String code = issued.path("code").asText();
        org.assertj.core.api.Assertions.assertThat(issued.path("status").asText()).isEqualTo("UNBOUND");

        JsonNode bound = json(postJson(session, "/api/v1/voucher-codes/" + code + "/bind", """
                {"memberId":%d,"idempotencyKey":"voucher-bind-1"}
                """.formatted(memberId), 200)).path("data");
        long voucherCodeId = bound.path("id").asLong();
        JsonNode bill = createBillWithLine(session, memberId, "voucher-bill-1");
        long billId = bill.path("bill").path("id").asLong();

        mockMvc.perform(get("/api/v1/bills/{id}/asset-options", billId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voucherOptions", hasSize(1)))
                .andExpect(jsonPath("$.data.voucherOptions[0].previewAmount").value(50));
        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"voucherCodeIds":[%d],"payments":[{"paymentMethodId":1,"amount":118}]}
                """.formatted(voucherCodeId), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(quote.path("assetAmount").decimalValue())
                .isEqualByComparingTo("50.0000");
        org.assertj.core.api.Assertions.assertThat(quote.path("assets").get(0).path("assetType").asText())
                .isEqualTo("VOUCHER");
        postJson(session, "/api/v1/bills/" + billId + "/settle", """
                {"quoteNo":"%s","idempotencyKey":"voucher-settle-1"}
                """.formatted(quote.path("quoteNo").asText()), 200);
        mockMvc.perform(get("/api/v1/voucher-codes/{code}", code).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REDEEMED"))
                .andExpect(jsonPath("$.data.redeemedBillId").value(billId));

        JsonNode submitted = json(postJson(session, "/api/v1/bills/" + billId + "/reversals", """
                {"reason":"代金券账单整单撤销","idempotencyKey":"voucher-reversal-request-1"}
                """, 201)).path("data");
        long reversalId = submitted.path("reversal").path("id").asLong();
        JsonNode approved = json(postJson(session, "/api/v1/reversals/" + reversalId + "/review", """
                {"approved":true,"version":"%s"}
                """.formatted(submitted.path("reversal").path("version").asText()), 200)).path("data");
        postJson(session, "/api/v1/reversals/" + reversalId + "/execute", """
                {"version":"%s","idempotencyKey":"voucher-reversal-execute-1"}
                """.formatted(approved.path("reversal").path("version").asText()), 200);
        mockMvc.perform(get("/api/v1/voucher-codes/{code}", code).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BOUND"))
                .andExpect(jsonPath("$.data.redeemedBillId").doesNotExist());
    }

    @Test
    void voucherCannotCrossMemberOrReuseAfterSettlement() throws Exception {
        MockHttpSession session = login();
        long ownerId = createMember(session, "13600009002");
        long otherId = createMember(session, "13600009003");
        JsonNode definition = json(postJson(session, "/api/v1/vouchers", """
                {"code":"DISC80","name":"八折券","benefitType":"DISCOUNT",
                 "faceAmount":0,"discountRate":0.8,"minSpend":0,"validDays":30}
                """, 201)).path("data");
        JsonNode voucher = json(postJson(session, "/api/v1/voucher-code-issues", """
                {"voucherId":%d,"count":1,"memberId":%d,"idempotencyKey":"voucher-issue-2"}
                """.formatted(definition.path("id").asLong(), ownerId), 201)).path("data").get(0);
        long voucherId = voucher.path("id").asLong();
        JsonNode otherBill = createBillWithLine(session, otherId, "voucher-other-bill");
        mockMvc.perform(post("/api/v1/bills/{id}/settlement/quote", otherBill.path("bill").path("id").asLong())
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"voucherCodeIds":[%d],"payments":[{"paymentMethodId":1,"amount":134.4}]}
                                """.formatted(voucherId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("代金券不属于当前账单会员"));
    }

    private JsonNode createBillWithLine(MockHttpSession session, long memberId, String key) throws Exception {
        JsonNode created = json(postJson(session, "/api/v1/bills", """
                {"memberId":%d,"storeId":2,"sourceType":"PC","personCount":1,"idempotencyKey":"%s"}
                """.formatted(memberId, key), 201)).path("data");
        return json(postJson(session, "/api/v1/bills/" + created.path("id").asLong() + "/lines", """
                {"serviceId":301,"quantity":1,"employeeId":101,"version":"%s"}
                """.formatted(created.path("version").asText()), 200)).path("data");
    }

    private long createMember(MockHttpSession session, String mobile) throws Exception {
        return json(postJson(session, "/api/v1/members", """
                {"fullName":"代金券测试会员","mobile":"%s","joinStoreId":2,"ownerStoreId":2}
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
