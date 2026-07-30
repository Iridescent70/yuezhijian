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
class TradeFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void manualBillCanBeSettledWithMixedPaymentIdempotently() throws Exception {
        MockHttpSession session = login();
        JsonNode created = createBill(session, "BILL-MIXED-001");
        long id = created.path("id").asLong();
        JsonNode bill = addLine(session, id, created.path("version").asText());

        String quoteResponse = mockMvc.perform(post("/api/v1/bills/{id}/settlement/quote", id)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payments":[
                                  {"paymentMethodId":1,"amount":68},
                                  {"paymentMethodId":3,"amount":100,"externalReference":"WX-TEST-001"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.differenceAmount").value(0))
                .andExpect(jsonPath("$.data.changeAmount").value(0))
                .andReturn().getResponse().getContentAsString();
        String quoteNo = objectMapper.readTree(quoteResponse).path("data").path("quoteNo").asText();

        String settleBody = """
                {"quoteNo":"%s","idempotencyKey":"SETTLE-MIXED-001"}
                """.formatted(quoteNo);
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/bills/{id}/settle", id)
                            .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                            .content(settleBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.bill.status").value("SETTLED"))
                    .andExpect(jsonPath("$.data.payments", hasSize(2)));
        }
        mockMvc.perform(get("/api/v1/bills/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bill.receivableAmount").value(168))
                .andExpect(jsonPath("$.data.bill.receivedAmount").value(168));
        org.assertj.core.api.Assertions.assertThat(bill.path("bill").path("status").asText())
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void electronicPaymentRequiresReferenceAndNonCashCannotCreateChange() throws Exception {
        MockHttpSession session = login();
        JsonNode created = createBill(session, "BILL-RULE-001");
        long id = created.path("id").asLong();
        addLine(session, id, created.path("version").asText());

        mockMvc.perform(post("/api/v1/bills/{id}/settlement/quote", id)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payments":[{"paymentMethodId":3,"amount":168}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40002"));
        mockMvc.perform(post("/api/v1/bills/{id}/settlement/quote", id)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payments":[{"paymentMethodId":2,"amount":200}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("非现金支付不能产生找零"));
    }

    @Test
    void arrivedAppointmentCanCreateBillWithServiceSnapshot() throws Exception {
        MockHttpSession session = login();
        String appointmentResponse = mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId":1001,"storeId":2,"startAt":"2026-08-10T10:00:00",
                                  "personCount":1,"employeeId":101,"workstationId":201,
                                  "serviceIds":[301],"designated":true,"idempotencyKey":"APT-BILL-001"
                                }
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode appointment = objectMapper.readTree(appointmentResponse).path("data");
        long appointmentId = appointment.path("id").asLong();
        JsonNode confirmed = transition(session, appointmentId, "confirm", appointment.path("version").asText());
        transition(session, appointmentId, "arrive", confirmed.path("appointment").path("version").asText());

        String billResponse = mockMvc.perform(post("/api/v1/appointments/{id}/create-bill", appointmentId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"copyServices\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        long billId = objectMapper.readTree(billResponse).path("data").path("id").asLong();
        mockMvc.perform(get("/api/v1/bills/{id}", billId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bill.appointmentId").value(appointmentId))
                .andExpect(jsonPath("$.data.lines[0].itemName").value("基础单色美甲"));
    }

    private JsonNode createBill(MockHttpSession session, String key) throws Exception {
        String response = mockMvc.perform(post("/api/v1/bills")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId":1001,"storeId":2,"sourceType":"PC","personCount":1,
                                  "note":"自动化账单","idempotencyKey":"%s"
                                }
                                """.formatted(key)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode addLine(MockHttpSession session, long id, String version) throws Exception {
        String response = mockMvc.perform(post("/api/v1/bills/{id}/lines", id)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":301,"quantity":1,"employeeId":101,"version":"%s"}
                                """.formatted(version)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode transition(MockHttpSession session, long id, String action, String version) throws Exception {
        String response = mockMvc.perform(post("/api/v1/appointments/{id}/{action}", id, action)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"%s\",\"personCount\":1}".formatted(version)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-admin","password":"TestPassword!2026"}
                                """))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
