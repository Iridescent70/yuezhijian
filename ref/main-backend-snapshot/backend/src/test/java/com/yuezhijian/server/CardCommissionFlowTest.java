package com.yuezhijian.server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
        "app.bootstrap.password=TestPassword!2026",
        "app.test.context=card-commission"
})
@AutoConfigureMockMvc
class CardCommissionFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void cardSaleCommissionIsPerCardAndRefundCompletesNegativeAdjustment() throws Exception {
        MockHttpSession session = login();
        createCardSalePlan(session, "CARD_REFUND_" + System.nanoTime());
        long memberId = createMember(session, "13600010001");
        JsonNode sale = purchase(session, memberId, 2, "card-commission-refund-sale");
        long cardId = sale.path("cards").get(0).path("id").asLong();
        long orderId = sale.path("orderId").asLong();

        List<JsonNode> originals = ledgers(session, "CARD_SALE", orderId);
        org.assertj.core.api.Assertions.assertThat(originals).hasSize(2);
        JsonNode original = originals.stream().filter(item -> item.path("sourceLineId").asLong() == cardId)
                .findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(original.path("baseAmount").decimalValue())
                .isEqualByComparingTo("1280.0000");
        org.assertj.core.api.Assertions.assertThat(original.path("commissionAmount").decimalValue())
                .isEqualByComparingTo("64.0000");

        JsonNode quote = json(postJson(session, "/api/v1/member-cards/" + cardId + "/refund-requests/quote", """
                {"feeAmount":0}
                """, 200)).path("data");
        JsonNode submitted = json(postJson(session, "/api/v1/member-cards/" + cardId + "/refund-requests", """
                {"quoteNo":"%s","refundMethodId":1,"storeId":2,"employeeId":101,
                 "reason":"售卡提成冲回测试","idempotencyKey":"card-commission-refund-request"}
                """.formatted(quote.path("quoteNo").asText()), 201)).path("data");
        long requestId = submitted.path("request").path("id").asLong();
        JsonNode approved = json(postJson(session, "/api/v1/card-refund-requests/" + requestId + "/review", """
                {"approved":true,"version":"%s"}
                """.formatted(submitted.path("request").path("version").asText()), 200)).path("data");
        JsonNode executed = json(postJson(session, "/api/v1/card-refund-requests/" + requestId + "/execute", """
                {"version":"%s","idempotencyKey":"card-commission-refund-execute"}
                """.formatted(approved.path("request").path("version").asText()), 200)).path("data");

        org.assertj.core.api.Assertions.assertThat(executed.path("request").path("commissionAdjustmentStatus").asText())
                .isEqualTo("COMPLETED");
        List<JsonNode> reversals = ledgers(session, "CARD_REFUND", requestId);
        org.assertj.core.api.Assertions.assertThat(reversals).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(reversals.getFirst().path("commissionAmount").decimalValue())
                .isEqualByComparingTo("-64.0000");
        org.assertj.core.api.Assertions.assertThat(reversals.getFirst().path("reversedLedgerId").asLong())
                .isEqualTo(original.path("id").asLong());
    }

    @Test
    void exchangeReversesOldCardAndCalculatesOnlySupplementForNewEmployee() throws Exception {
        MockHttpSession session = login();
        createCardSalePlan(session, "CARD_EXCHANGE_" + System.nanoTime());
        long memberId = createMember(session, "13600010002");
        JsonNode sale = purchase(session, memberId, 1, "card-commission-exchange-sale");
        long cardId = sale.path("cards").get(0).path("id").asLong();
        long targetTypeId = createTargetCardType(session, "CARD_COMM_TARGET_" + System.nanoTime());
        JsonNode quote = json(postJson(session, "/api/v1/member-cards/" + cardId + "/exchange/quote", """
                {"targetCardTypeId":%d}
                """.formatted(targetTypeId), 200)).path("data");
        JsonNode exchange = json(postJson(session, "/api/v1/member-cards/" + cardId + "/exchange", """
                {"quoteNo":"%s","storeId":2,"employeeId":101,
                 "payments":[{"paymentMethodId":1,"amount":1400}],
                 "idempotencyKey":"card-commission-exchange-execute"}
                """.formatted(quote.path("quoteNo").asText()), 200)).path("data");

        long exchangeId = exchange.path("exchangeId").asLong();
        long newCardId = exchange.path("newCard").path("id").asLong();
        List<JsonNode> facts = ledgers(session, "CARD_EXCHANGE", exchangeId);
        org.assertj.core.api.Assertions.assertThat(facts).hasSize(2);
        JsonNode negative = facts.stream().filter(item -> item.path("commissionAmount").decimalValue().signum() < 0)
                .findFirst().orElseThrow();
        JsonNode positive = facts.stream().filter(item -> item.path("commissionAmount").decimalValue().signum() > 0)
                .findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(negative.path("commissionAmount").decimalValue())
                .isEqualByComparingTo("-64.0000");
        org.assertj.core.api.Assertions.assertThat(positive.path("sourceLineId").asLong()).isEqualTo(newCardId);
        org.assertj.core.api.Assertions.assertThat(positive.path("baseAmount").decimalValue())
                .isEqualByComparingTo("1400.0000");
        org.assertj.core.api.Assertions.assertThat(positive.path("commissionAmount").decimalValue())
                .isEqualByComparingTo("70.0000");
    }

    @Test
    void cardPaymentUsesConsumePlanInsteadOfNormalServicePlan() throws Exception {
        MockHttpSession session = login();
        createCommissionPlan(session, "CARD_CONSUME_" + System.nanoTime(), "次卡实耗提成", "CARD_CONSUME", "0.02");
        long memberId = createMember(session, "13600010003");
        JsonNode sale = purchase(session, memberId, 1, "card-commission-consume-sale");
        long cardId = sale.path("cards").get(0).path("id").asLong();
        JsonNode created = json(postJson(session, "/api/v1/bills", """
                {"memberId":%d,"storeId":2,"sourceType":"PC","personCount":1,
                 "idempotencyKey":"card-commission-consume-bill"}
                """.formatted(memberId), 201)).path("data");
        JsonNode bill = json(postJson(session, "/api/v1/bills/" + created.path("id").asLong() + "/lines", """
                {"serviceId":301,"quantity":1,"employeeId":101,"version":"%s"}
                """.formatted(created.path("version").asText()), 200)).path("data");
        long billId = bill.path("bill").path("id").asLong();
        long lineId = bill.path("lines").get(0).path("id").asLong();
        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"payments":[],"cards":[{"billLineId":%d,"memberCardId":%d}]}
                """.formatted(lineId, cardId), 200)).path("data");
        postJson(session, "/api/v1/bills/" + billId + "/settle", """
                {"quoteNo":"%s","idempotencyKey":"card-commission-consume-settle"}
                """.formatted(quote.path("quoteNo").asText()), 200);

        List<JsonNode> facts = ledgers(session, "BILL", billId);
        org.assertj.core.api.Assertions.assertThat(facts).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(facts.getFirst().path("commissionType").asText())
                .isEqualTo("CARD_CONSUME");
        org.assertj.core.api.Assertions.assertThat(facts.getFirst().path("commissionAmount").decimalValue())
                .isEqualByComparingTo("3.3600");
    }

    @Test
    void refundAfterTwoTransfersReversesOriginalCardSaleCommission() throws Exception {
        MockHttpSession session = login();
        createCardSalePlan(session, "CARD_LINEAGE_" + System.nanoTime());
        long sourceMemberId = createMember(session, "13600010004");
        long middleMemberId = createMember(session, "13600010005");
        long targetMemberId = createMember(session, "13600010006");
        JsonNode sale = purchase(session, sourceMemberId, 1, "card-commission-lineage-sale");
        long sourceCardId = sale.path("cards").get(0).path("id").asLong();
        JsonNode original = ledgers(session, "CARD_SALE", sale.path("orderId").asLong()).getFirst();

        long middleCardId = transfer(
                session, sourceCardId, middleMemberId, "card-commission-lineage-transfer-1");
        long targetCardId = transfer(
                session, middleCardId, targetMemberId, "card-commission-lineage-transfer-2");
        JsonNode executed = refund(
                session, targetCardId, "card-commission-lineage-refund", "连续转赠后退卡");

        long requestId = executed.path("request").path("id").asLong();
        org.assertj.core.api.Assertions.assertThat(executed.path("request").path("commissionAdjustmentStatus").asText())
                .isEqualTo("COMPLETED");
        List<JsonNode> reversals = ledgers(session, "CARD_REFUND", requestId);
        org.assertj.core.api.Assertions.assertThat(reversals).hasSize(1);
        JsonNode reversal = reversals.getFirst();
        org.assertj.core.api.Assertions.assertThat(reversal.path("sourceLineId").asLong()).isEqualTo(targetCardId);
        org.assertj.core.api.Assertions.assertThat(reversal.path("reversedLedgerId").asLong())
                .isEqualTo(original.path("id").asLong());
        org.assertj.core.api.Assertions.assertThat(reversal.path("commissionAmount").decimalValue())
                .isEqualByComparingTo("-64.0000");
    }

    @Test
    void exchangeAfterTransferReversesOriginalSaleAndCalculatesSupplement() throws Exception {
        MockHttpSession session = login();
        createCardSalePlan(session, "CARD_TRANSFER_EXCHANGE_" + System.nanoTime());
        long sourceMemberId = createMember(session, "13600010007");
        long recipientMemberId = createMember(session, "13600010008");
        JsonNode sale = purchase(session, sourceMemberId, 1, "card-commission-transfer-exchange-sale");
        JsonNode original = ledgers(session, "CARD_SALE", sale.path("orderId").asLong()).getFirst();
        long transferredCardId = transfer(
                session, sale.path("cards").get(0).path("id").asLong(), recipientMemberId,
                "card-commission-transfer-exchange-transfer");
        long targetTypeId = createTargetCardType(session, "CARD_TRANSFER_TARGET_" + System.nanoTime());
        JsonNode quote = json(postJson(
                session, "/api/v1/member-cards/" + transferredCardId + "/exchange/quote", """
                {"targetCardTypeId":%d}
                """.formatted(targetTypeId), 200)).path("data");
        JsonNode exchange = json(postJson(
                session, "/api/v1/member-cards/" + transferredCardId + "/exchange", """
                {"quoteNo":"%s","storeId":2,"employeeId":101,
                 "payments":[{"paymentMethodId":1,"amount":1400}],
                 "idempotencyKey":"card-commission-transfer-exchange-execute"}
                """.formatted(quote.path("quoteNo").asText()), 200)).path("data");

        List<JsonNode> facts = ledgers(session, "CARD_EXCHANGE", exchange.path("exchangeId").asLong());
        org.assertj.core.api.Assertions.assertThat(facts).hasSize(2);
        JsonNode negative = facts.stream().filter(item -> item.path("commissionAmount").decimalValue().signum() < 0)
                .findFirst().orElseThrow();
        JsonNode positive = facts.stream().filter(item -> item.path("commissionAmount").decimalValue().signum() > 0)
                .findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(negative.path("sourceLineId").asLong())
                .isEqualTo(transferredCardId);
        org.assertj.core.api.Assertions.assertThat(negative.path("reversedLedgerId").asLong())
                .isEqualTo(original.path("id").asLong());
        org.assertj.core.api.Assertions.assertThat(negative.path("commissionAmount").decimalValue())
                .isEqualByComparingTo("-64.0000");
        org.assertj.core.api.Assertions.assertThat(positive.path("sourceLineId").asLong())
                .isEqualTo(exchange.path("newCard").path("id").asLong());
        org.assertj.core.api.Assertions.assertThat(positive.path("commissionAmount").decimalValue())
                .isEqualByComparingTo("70.0000");
    }

    private void createCardSalePlan(MockHttpSession session, String code) throws Exception {
        createCommissionPlan(session, code, "示范店售卡提成", "CARD_SALE", "0.05");
    }

    private void createCommissionPlan(
            MockHttpSession session, String code, String name, String scene, String rate) throws Exception {
        postJson(session, "/api/v1/commission-plans", """
                {"code":"%s","name":"%s","scene":"%s","calculationMode":"RATE",
                 "rate":%s,"storeId":2,"positionId":1,"effectiveFrom":"%s"}
                """.formatted(code, name, scene, rate, LocalDate.now().minusDays(1)), 201);
    }

    private JsonNode purchase(MockHttpSession session, long memberId, int quantity, String key) throws Exception {
        return json(postJson(session, "/api/v1/members/" + memberId + "/cards", """
                {"cardTypeId":501,"quantity":%d,"storeId":2,"paymentMethodId":1,
                 "salesEmployeeId":101,"idempotencyKey":"%s"}
                """.formatted(quantity, key), 201)).path("data");
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

    private long transfer(MockHttpSession session, long cardId, long recipientMemberId, String key) throws Exception {
        JsonNode card = json(mockMvc.perform(get("/api/v1/member-cards/" + cardId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("data").path("card");
        return json(postJson(session, "/api/v1/member-cards/" + cardId + "/transfer", """
                {"recipientMemberId":%d,"expiresAt":"%s","storeId":2,"employeeId":101,
                 "reason":"提成谱系测试","sourceCardVersion":"%s","idempotencyKey":"%s"}
                """.formatted(recipientMemberId, LocalDateTime.now().plusDays(200).withNano(0),
                card.path("version").asText(), key), 200)).path("data").path("targetCard").path("id").asLong();
    }

    private JsonNode refund(MockHttpSession session, long cardId, String key, String reason) throws Exception {
        JsonNode quote = json(postJson(session, "/api/v1/member-cards/" + cardId + "/refund-requests/quote", """
                {"feeAmount":0}
                """, 200)).path("data");
        JsonNode submitted = json(postJson(session, "/api/v1/member-cards/" + cardId + "/refund-requests", """
                {"quoteNo":"%s","refundMethodId":1,"storeId":2,"employeeId":101,
                 "reason":"%s","idempotencyKey":"%s-request"}
                """.formatted(quote.path("quoteNo").asText(), reason, key), 201)).path("data");
        long requestId = submitted.path("request").path("id").asLong();
        JsonNode approved = json(postJson(session, "/api/v1/card-refund-requests/" + requestId + "/review", """
                {"approved":true,"version":"%s"}
                """.formatted(submitted.path("request").path("version").asText()), 200)).path("data");
        return json(postJson(session, "/api/v1/card-refund-requests/" + requestId + "/execute", """
                {"version":"%s","idempotencyKey":"%s-execute"}
                """.formatted(approved.path("request").path("version").asText(), key), 200)).path("data");
    }

    private List<JsonNode> ledgers(MockHttpSession session, String sourceType, long sourceId) throws Exception {
        JsonNode data = json(mockMvc.perform(get("/api/v1/commission-ledgers").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        List<JsonNode> result = new ArrayList<>();
        data.forEach(item -> {
            if (sourceType.equals(item.path("sourceType").asText()) && item.path("sourceId").asLong() == sourceId) {
                result.add(item);
            }
        });
        return result;
    }

    private long createMember(MockHttpSession session, String mobile) throws Exception {
        return json(postJson(session, "/api/v1/members", """
                {"fullName":"卡提成测试会员","mobile":"%s","joinStoreId":2,"ownerStoreId":2}
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
