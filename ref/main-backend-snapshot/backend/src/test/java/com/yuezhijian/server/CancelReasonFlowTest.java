package com.yuezhijian.server;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.StreamSupport;
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
class CancelReasonFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void managedReasonsDriveAppointmentAndBillOperations() throws Exception {
        MockHttpSession session = login();
        JsonNode appointmentReason = createReason(
                session, "APPOINTMENT", "TEST_APT_CANCEL_948", "自动化预约取消", false);
        long appointmentReasonId = appointmentReason.path("id").asLong();
        String appointmentReasonVersion = appointmentReason.path("version").asText();
        assertOptionExists(session, "/api/v1/appointment-cancel-reasons", "TEST_APT_CANCEL_948", true);

        JsonNode appointment = data(postJson(session, "/api/v1/appointments", """
                {
                  "memberId":1001,"storeId":2,"sourceType":"PC","appointmentType":"IN_STORE",
                  "startAt":"2026-08-27T16:00:00","personCount":1,"employeeId":102,
                  "workstationId":202,"serviceIds":[301],"designated":true,
                  "idempotencyKey":"APT-CANCEL-CONFIG-948"
                }
                """));
        long appointmentId = appointment.path("id").asLong();
        mockMvc.perform(post("/api/v1/appointments/{id}/cancel", appointmentId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasonCode":"TEST_APT_CANCEL_948","version":"%s"}
                                """.formatted(appointment.path("version").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointment.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.history[1].reasonCode").value("TEST_APT_CANCEL_948"));

        JsonNode billReason = createReason(
                session, "BILL", "TEST_BILL_VOID_948", "自动化账单作废", true);
        assertOptionExists(session, "/api/v1/bill-cancel-reasons", "TEST_BILL_VOID_948", true);
        JsonNode bill = data(postJson(session, "/api/v1/bills", """
                {
                  "memberId":1001,"storeId":2,"sourceType":"PC","personCount":1,
                  "idempotencyKey":"BILL-CANCEL-CONFIG-948"
                }
                """));
        long billId = bill.path("id").asLong();
        String voidBody = """
                {"reasonCode":"TEST_BILL_VOID_948","version":"%s"}
                """.formatted(bill.path("version").asText());
        mockMvc.perform(post("/api/v1/bills/{id}/void", billId).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(voidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("所选原因必须填写说明"));
        mockMvc.perform(post("/api/v1/bills/{id}/void", billId).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reasonCode":"TEST_BILL_VOID_948","note":"测试账单录入错误",
                                  "version":"%s"
                                }
                                """.formatted(bill.path("version").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bill.status").value("VOIDED"))
                .andExpect(jsonPath("$.data.history[1].reasonCode").value("TEST_BILL_VOID_948"));

        JsonNode disabled = data(putJson(session, "/api/v1/cancel-reasons/" + appointmentReasonId, """
                {
                  "name":"自动化预约取消","requiresNote":false,"sortNo":10,
                  "status":"DISABLED","version":"%s"
                }
                """.formatted(appointmentReasonVersion)));
        org.assertj.core.api.Assertions.assertThat(disabled.path("status").asText()).isEqualTo("DISABLED");
        assertOptionExists(session, "/api/v1/appointment-cancel-reasons", "TEST_APT_CANCEL_948", false);

        mockMvc.perform(put("/api/v1/cancel-reasons/{id}", appointmentReasonId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"过期修改","requiresNote":false,"sortNo":10,
                                  "status":"ACTIVE","version":"%s"
                                }
                                """.formatted(appointmentReasonVersion)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/cancel-reasons").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "businessType":"BILL","code":"TEST_BILL_VOID_948",
                                  "name":"重复编号","requiresNote":false,"sortNo":30
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/audit-logs").session(session)
                        .param("objectType", "CANCEL_REASON")
                        .param("objectId", String.valueOf(appointmentReasonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(2)));
        org.assertj.core.api.Assertions.assertThat(billReason.path("requiresNote").asBoolean()).isTrue();
    }

    private JsonNode createReason(
            MockHttpSession session, String businessType, String code, String name, boolean requiresNote)
            throws Exception {
        return data(postJson(session, "/api/v1/cancel-reasons", """
                {
                  "businessType":"%s","code":"%s","name":"%s",
                  "requiresNote":%s,"sortNo":10
                }
                """.formatted(businessType, code, name, requiresNote)));
    }

    private void assertOptionExists(MockHttpSession session, String path, String code, boolean expected)
            throws Exception {
        String body = mockMvc.perform(get(path).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        boolean exists = StreamSupport.stream(data(body).spliterator(), false)
                .anyMatch(item -> code.equals(item.path("code").asText()));
        org.assertj.core.api.Assertions.assertThat(exists).isEqualTo(expected);
    }

    private String postJson(MockHttpSession session, String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String putJson(MockHttpSession session, String path, String body) throws Exception {
        return mockMvc.perform(put(path).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode data(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        org.assertj.core.api.Assertions.assertThat(root.path("code").asText()).isEqualTo("0");
        return root.path("data");
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"TestPassword!2026\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
