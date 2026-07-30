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
class AppointmentFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void appointmentCanMoveThroughFullServiceLifecycle() throws Exception {
        MockHttpSession session = login();
        JsonNode created = create(session, "APT-FLOW-001", "2026-08-01T10:00:00", 101, 201);
        long id = created.path("id").asLong();
        String version = created.path("version").asText();

        JsonNode confirmed = transition(session, id, "confirm", version, null, null);
        JsonNode arrived = transition(session, id, "arrive", confirmed.path("appointment").path("version").asText(), null, 2);
        JsonNode serving = transition(session, id, "start", arrived.path("appointment").path("version").asText(), null, null);
        JsonNode completed = transition(session, id, "complete", serving.path("appointment").path("version").asText(), null, null);

        mockMvc.perform(get("/api/v1/appointments/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointment.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.appointment.personCount").value(2))
                .andExpect(jsonPath("$.data.appointment.maskedMobile").value("*******1001"))
                .andExpect(jsonPath("$.data.services", hasSize(1)))
                .andExpect(jsonPath("$.data.history", hasSize(5)));
        org.assertj.core.api.Assertions.assertThat(completed.path("appointment").path("status").asText())
                .isEqualTo("COMPLETED");
    }

    @Test
    void appointmentCreationIsIdempotentAndRejectsResourceConflict() throws Exception {
        MockHttpSession session = login();
        JsonNode first = create(session, "APT-IDEMPOTENT-001", "2026-08-02T14:00:00", 101, 201);
        JsonNode retried = create(session, "APT-IDEMPOTENT-001", "2026-08-02T14:00:00", 101, 201);
        org.assertj.core.api.Assertions.assertThat(retried.path("id").asLong()).isEqualTo(first.path("id").asLong());

        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("APT-CONFLICT-001", "2026-08-02T14:30:00", 101, 202)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40901"));
    }

    @Test
    void cancelledAppointmentReleasesSlotAndKeepsReason() throws Exception {
        MockHttpSession session = login();
        JsonNode created = create(session, "APT-CANCEL-001", "2026-08-03T11:00:00", 102, 202);
        long id = created.path("id").asLong();
        JsonNode cancelled = transition(
                session, id, "cancel", created.path("version").asText(), "CUSTOMER_CHANGE", null);
        org.assertj.core.api.Assertions.assertThat(cancelled.path("appointment").path("status").asText())
                .isEqualTo("CANCELLED");
        create(session, "APT-CANCEL-REBOOK", "2026-08-03T11:00:00", 102, 202);
    }

    private JsonNode create(
            MockHttpSession session, String key, String startAt, long employeeId, long workstationId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(key, startAt, employeeId, workstationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private String createBody(String key, String startAt, long employeeId, long workstationId) {
        return """
                {
                  "memberId":1001,
                  "storeId":2,
                  "sourceType":"PC",
                  "appointmentType":"IN_STORE",
                  "startAt":"%s",
                  "personCount":1,
                  "employeeId":%d,
                  "workstationId":%d,
                  "serviceIds":[301],
                  "designated":true,
                  "note":"自动化预约",
                  "idempotencyKey":"%s"
                }
                """.formatted(startAt, employeeId, workstationId, key);
    }

    private JsonNode transition(
            MockHttpSession session, long id, String action, String version,
            String reasonCode, Integer personCount) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "version", version,
                "reasonCode", reasonCode == null ? "" : reasonCode,
                "personCount", personCount == null ? 1 : personCount));
        String response = mockMvc.perform(post("/api/v1/appointments/{id}/{action}", id, action)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-admin","password":"TestPassword!2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
