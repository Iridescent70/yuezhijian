package com.yuezhijian.server;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class BillMaintenanceFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void lineMaintenanceAndDiscountKeepBillAmountsConsistent() throws Exception {
        MockHttpSession session = login();
        JsonNode created = json(postJson(session, "/api/v1/bills", """
                {"memberId":1001,"storeId":2,"sourceType":"PC","personCount":1,
                 "idempotencyKey":"bill-maintenance-1"}
                """, 201)).path("data");
        long billId = created.path("id").asLong();
        JsonNode first = addLine(session, billId, 301, created.path("version").asText());
        long firstLineId = first.path("lines").get(0).path("id").asLong();
        JsonNode updated = json(mockMvc.perform(put("/api/v1/bills/{id}/lines/{lineId}", billId, firstLineId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":2,"employeeId":101,"note":"双人项目","version":"%s"}
                                """.formatted(first.path("bill").path("version").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bill.originalAmount").value(336))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(2))
                .andReturn().getResponse().getContentAsString()).path("data");
        JsonNode withSecond = addLine(
                session, billId, 302, updated.path("bill").path("version").asText());
        long secondLineId = withSecond.path("lines").get(1).path("id").asLong();

        JsonNode discounted = json(postJson(session, "/api/v1/bills/" + billId + "/discounts", """
                {"discountType":"RATE","value":0.9,"reason":"店长九折授权","version":"%s"}
                """.formatted(withSecond.path("bill").path("version").asText()), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(discounted.path("bill").path("originalAmount").decimalValue())
                .isEqualByComparingTo("634.0000");
        org.assertj.core.api.Assertions.assertThat(discounted.path("bill").path("discountAmount").decimalValue())
                .isEqualByComparingTo("63.4000");
        org.assertj.core.api.Assertions.assertThat(discounted.path("bill").path("receivableAmount").decimalValue())
                .isEqualByComparingTo("570.6000");
        org.assertj.core.api.Assertions.assertThat(discounted.path("discounts").size()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(discounted.path("lines").get(0).path("discountAmount").decimalValue())
                .isEqualByComparingTo("33.6000");
        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"payments":[{"paymentMethodId":1,"amount":570.6}]}
                """, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(quote.path("receivableAmount").decimalValue())
                .isEqualByComparingTo("570.6000");

        JsonNode editedAfterDiscount = json(mockMvc.perform(
                        put("/api/v1/bills/{id}/lines/{lineId}", billId, firstLineId)
                                .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"quantity":1,"employeeId":101,"version":"%s"}
                                        """.formatted(discounted.path("bill").path("version").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bill.discountAmount").value(0))
                .andExpect(jsonPath("$.data.bill.receivableAmount").value(466))
                .andExpect(jsonPath("$.data.discounts", hasSize(0)))
                .andReturn().getResponse().getContentAsString()).path("data");

        mockMvc.perform(post("/api/v1/bills/{id}/settle", billId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quoteNo":"%s","idempotencyKey":"bill-maintenance-stale-quote"}
                                """.formatted(quote.path("quoteNo").asText())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("账单已发生变化，请重新试算"));

        mockMvc.perform(delete("/api/v1/bills/{id}/lines/{lineId}", billId, secondLineId)
                        .param("version", editedAfterDiscount.path("bill").path("version").asText())
                        .with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines", hasSize(1)))
                .andExpect(jsonPath("$.data.bill.originalAmount").value(168))
                .andExpect(jsonPath("$.data.bill.receivableAmount").value(168));
    }

    @Test
    void staleLineVersionAndInvalidDiscountAreRejected() throws Exception {
        MockHttpSession session = login();
        JsonNode created = json(postJson(session, "/api/v1/bills", """
                {"memberId":1002,"storeId":2,"sourceType":"PC","personCount":1,
                 "idempotencyKey":"bill-maintenance-2"}
                """, 201)).path("data");
        JsonNode bill = addLine(session, created.path("id").asLong(), 301, created.path("version").asText());
        long billId = bill.path("bill").path("id").asLong();
        long lineId = bill.path("lines").get(0).path("id").asLong();

        mockMvc.perform(put("/api/v1/bills/{id}/lines/{lineId}", billId, lineId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":2,"version":"1"}
                                """))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/bills/{id}/discounts", billId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"discountType":"RATE","value":0,"reason":"错误折扣","version":"%s"}
                                """.formatted(bill.path("bill").path("version").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("折扣率必须大于0且不超过1"));
    }

    private JsonNode addLine(MockHttpSession session, long billId, long serviceId, String version) throws Exception {
        return json(postJson(session, "/api/v1/bills/" + billId + "/lines", """
                {"serviceId":%d,"quantity":1,"employeeId":101,"version":"%s"}
                """.formatted(serviceId, version), 200)).path("data");
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
