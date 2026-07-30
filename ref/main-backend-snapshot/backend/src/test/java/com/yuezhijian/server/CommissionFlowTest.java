package com.yuezhijian.server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
        "app.bootstrap.password=TestPassword!2026"
})
@AutoConfigureMockMvc
class CommissionFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void settledBillCreatesVersionedCommissionAndReversalCreatesLinkedNegativeLedger() throws Exception {
        MockHttpSession session = login();
        String code = "SERVICE_RATE_" + System.nanoTime();
        JsonNode plan = json(postJson(session, "/api/v1/commission-plans", """
                {"code":"%s","name":"示范店技师服务提成","scene":"SERVICE","calculationMode":"RATE",
                 "rate":0.1,"storeId":2,"positionId":1,"effectiveFrom":"%s"}
                """.formatted(code, LocalDate.now().minusDays(1)), 201)).path("data");

        JsonNode created = json(postJson(session, "/api/v1/bills", """
                {"memberId":1001,"storeId":2,"sourceType":"PC","personCount":1,
                 "idempotencyKey":"commission-bill-%s"}
                """.formatted(code), 201)).path("data");
        long billId = created.path("id").asLong();
        JsonNode bill = json(postJson(session, "/api/v1/bills/" + billId + "/lines", """
                {"serviceId":301,"quantity":1,"employeeId":101,"version":"%s"}
                """.formatted(created.path("version").asText()), 200)).path("data");
        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"payments":[{"paymentMethodId":1,"amount":168}]}
                """, 200)).path("data");
        String settleBody = """
                {"quoteNo":"%s","idempotencyKey":"commission-settle-%s"}
                """.formatted(quote.path("quoteNo").asText(), code);
        postJson(session, "/api/v1/bills/" + billId + "/settle", settleBody, 200);
        postJson(session, "/api/v1/bills/" + billId + "/settle", settleBody, 200);

        List<JsonNode> originals = ledgersForBill(session, billId, "BILL");
        org.assertj.core.api.Assertions.assertThat(originals).hasSize(1);
        JsonNode original = originals.getFirst();
        org.assertj.core.api.Assertions.assertThat(original.path("baseAmount").decimalValue())
                .isEqualByComparingTo("168.0000");
        org.assertj.core.api.Assertions.assertThat(original.path("commissionAmount").decimalValue())
                .isEqualByComparingTo("16.8000");
        org.assertj.core.api.Assertions.assertThat(original.path("planRuleVersion").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(original.path("formulaSnapshot").asText()).contains("×0.100000");

        json(putJson(session, "/api/v1/commission-plans/" + plan.path("id").asLong(), """
                {"name":"示范店技师服务提成","scene":"SERVICE","calculationMode":"RATE",
                 "rate":0.2,"storeId":2,"positionId":1,"effectiveFrom":"%s",
                 "status":"ACTIVE","version":"%s"}
                """.formatted(LocalDate.now().minusDays(1), plan.path("version").asText()), 200));
        JsonNode afterUpdate = ledgersForBill(session, billId, "BILL").getFirst();
        org.assertj.core.api.Assertions.assertThat(afterUpdate.path("commissionAmount").decimalValue())
                .isEqualByComparingTo("16.8000");
        org.assertj.core.api.Assertions.assertThat(afterUpdate.path("planRuleVersion").asInt()).isEqualTo(1);

        JsonNode submitted = json(postJson(session, "/api/v1/bills/" + billId + "/reversals", """
                {"reason":"提成冲回联动测试","idempotencyKey":"commission-reversal-request-%s"}
                """.formatted(code), 201)).path("data");
        long reversalId = submitted.path("reversal").path("id").asLong();
        JsonNode approved = json(postJson(session, "/api/v1/reversals/" + reversalId + "/review", """
                {"approved":true,"version":"%s"}
                """.formatted(submitted.path("reversal").path("version").asText()), 200)).path("data");
        postJson(session, "/api/v1/reversals/" + reversalId + "/execute", """
                {"version":"%s","idempotencyKey":"commission-reversal-execute-%s"}
                """.formatted(approved.path("reversal").path("version").asText(), code), 200);

        List<JsonNode> reversals = ledgersForSource(session, reversalId, "BILL_REVERSAL");
        org.assertj.core.api.Assertions.assertThat(reversals).hasSize(1);
        JsonNode negative = reversals.getFirst();
        org.assertj.core.api.Assertions.assertThat(negative.path("commissionAmount").decimalValue())
                .isEqualByComparingTo("-16.8000");
        org.assertj.core.api.Assertions.assertThat(negative.path("reversedLedgerId").asLong())
                .isEqualTo(original.path("id").asLong());
    }

    @Test
    void simulatorCalculatesFixedPlanWithoutWritingLedger() throws Exception {
        MockHttpSession session = login();
        String code = "FIXED_SIM_" + System.nanoTime();
        JsonNode plan = json(postJson(session, "/api/v1/commission-plans", """
                {"code":"%s","name":"固定奖励测算","scene":"CARD_SALE","calculationMode":"FIXED",
                 "fixedAmount":15,"storeId":2,"positionId":1,"effectiveFrom":"%s"}
                """.formatted(code, LocalDate.now().minusDays(1)), 201)).path("data");
        int ledgerCountBefore = allLedgers(session).size();

        JsonNode result = json(postJson(
                session, "/api/v1/commission-plans/" + plan.path("id").asLong() + "/simulate", """
                {"employeeId":101,"storeId":2,"businessDate":"%s",
                 "performanceAmount":800,"itemCount":3}
                """.formatted(LocalDate.now()), 200)).path("data");

        org.assertj.core.api.Assertions.assertThat(result.path("applicable").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(result.path("commissionAmount").decimalValue())
                .isEqualByComparingTo("45.0000");
        org.assertj.core.api.Assertions.assertThat(result.path("calculationSteps").get(1).asText())
                .contains("15.0000×3=45.0000");
        org.assertj.core.api.Assertions.assertThat(result.path("warnings").size()).isZero();
        org.assertj.core.api.Assertions.assertThat(allLedgers(session)).hasSize(ledgerCountBefore);

        JsonNode outsideEffectiveDate = json(postJson(
                session, "/api/v1/commission-plans/" + plan.path("id").asLong() + "/simulate", """
                {"employeeId":101,"storeId":2,"businessDate":"%s",
                 "performanceAmount":800,"itemCount":3}
                """.formatted(LocalDate.now().minusDays(2)), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(outsideEffectiveDate.path("applicable").asBoolean()).isFalse();
        org.assertj.core.api.Assertions.assertThat(outsideEffectiveDate.path("warnings").get(0).asText())
                .isEqualTo("业务日期不在方案有效期内");
        org.assertj.core.api.Assertions.assertThat(allLedgers(session)).hasSize(ledgerCountBefore);
    }

    private List<JsonNode> ledgersForBill(MockHttpSession session, long billId, String sourceType) throws Exception {
        return ledgersForSource(session, billId, sourceType);
    }

    private List<JsonNode> ledgersForSource(MockHttpSession session, long sourceId, String sourceType) throws Exception {
        JsonNode data = allLedgers(session);
        List<JsonNode> result = new ArrayList<>();
        data.forEach(item -> {
            if (item.path("sourceId").asLong() == sourceId && sourceType.equals(item.path("sourceType").asText())) {
                result.add(item);
            }
        });
        return result;
    }

    private JsonNode allLedgers(MockHttpSession session) throws Exception {
        return json(mockMvc.perform(get("/api/v1/commission-ledgers").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private String postJson(MockHttpSession session, String url, String content, int code) throws Exception {
        return mockMvc.perform(post(url).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().is(code)).andReturn().getResponse().getContentAsString();
    }

    private String putJson(MockHttpSession session, String url, String content, int code) throws Exception {
        return mockMvc.perform(put(url).with(csrf()).session(session)
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
