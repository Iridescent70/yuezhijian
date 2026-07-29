package com.yuezhijian.server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class VisitFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void settlementCreatesOneMultiTechnicianTaskAndRecordsCompleteTheVisit() throws Exception {
        MockHttpSession session = login();
        String key = String.valueOf(System.nanoTime());
        JsonNode created = createBill(session, "visit-bill-" + key);
        long billId = created.path("id").asLong();
        JsonNode first = addLine(session, billId, 301, 101, created.path("version").asText());
        addLine(session, billId, 302, 102, first.path("bill").path("version").asText());
        settle(session, billId, "visit-settle-" + key, 466);

        JsonNode tasks = json(getJson(session, "/api/v1/visit-tasks?storeId=2", 200)).path("data");
        JsonNode task = null;
        for (JsonNode item : tasks) if (item.path("billId").asLong() == billId) task = item;
        org.assertj.core.api.Assertions.assertThat(task).isNotNull();
        org.assertj.core.api.Assertions.assertThat(task.path("participantCount").asInt()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(task.path("completedCount").asInt()).isZero();
        long taskId = task.path("id").asLong();

        JsonNode firstRecord = json(postJson(session, "/api/v1/visit-tasks/" + taskId + "/records", """
                {"employeeId":101,"resultCode":"CONTACTED","satisfactionScore":5,
                 "complaintFlag":false,"content":"会员对本次服务满意"}
                """, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(firstRecord.path("task").path("status").asText())
                .isEqualTo("PENDING");
        org.assertj.core.api.Assertions.assertThat(firstRecord.path("task").path("completedCount").asInt())
                .isEqualTo(1);

        JsonNode followUp = json(postJson(session, "/api/v1/visit-tasks/" + taskId + "/records", """
                {"employeeId":102,"resultCode":"NO_ANSWER","complaintFlag":false,
                 "content":"首次电话未接通","nextFollowAt":"%s"}
                """.formatted(LocalDateTime.now().plusDays(2).withNano(0)), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(followUp.path("task").path("completedCount").asInt())
                .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(followUp.path("records").size()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(feedbackForBill(session, billId)).isNull();

        JsonNode completed = json(postJson(session, "/api/v1/visit-tasks/" + taskId + "/records", """
                {"employeeId":102,"resultCode":"CONTACTED","satisfactionScore":2,
                 "complaintFlag":true,"content":"会员反馈款式保持时间不足，已登记店长跟进"}
                """, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(completed.path("task").path("status").asText())
                .isEqualTo("COMPLETED");
        org.assertj.core.api.Assertions.assertThat(completed.path("task").path("complaintFlag").asBoolean())
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(completed.path("task").path("completedCount").asInt())
                .isEqualTo(2);

        JsonNode concluded = json(postJson(session, "/api/v1/visit-tasks/" + taskId + "/complete", """
                {"conclusion":"两位技师均已回访，客诉转店长继续处理"}
                """, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(concluded.path("task").path("conclusion").asText())
                .contains("客诉转店长");

        JsonNode feedback = feedbackForBill(session, billId);
        org.assertj.core.api.Assertions.assertThat(feedback).isNotNull();
        org.assertj.core.api.Assertions.assertThat(feedback.path("score").asInt()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(feedback.path("status").asText()).isEqualTo("OPEN");
        org.assertj.core.api.Assertions.assertThat(feedback.path("dueHours").asInt()).isEqualTo(24);
        org.assertj.core.api.Assertions.assertThat(feedback.path("dueAt").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(feedback.path("overdue").asBoolean()).isFalse();
        long feedbackId = feedback.path("id").asLong();

        JsonNode assigned = json(postJson(session, "/api/v1/service-feedback/" + feedbackId + "/handle", """
                {"action":"ASSIGN","handlerId":101,"content":"由店长安排安然负责跟进"}
                """, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(assigned.path("feedback").path("status").asText())
                .isEqualTo("PROCESSING");
        postJson(session, "/api/v1/service-feedback/" + feedbackId + "/handle", """
                {"action":"NOTE","content":"已与会员沟通并安排免费修复"}
                """, 200);
        JsonNode resolved = json(postJson(session, "/api/v1/service-feedback/" + feedbackId + "/handle", """
                {"action":"RESOLVE","result":"会员已完成免费修复并确认满意"}
                """, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(resolved.path("feedback").path("status").asText())
                .isEqualTo("RESOLVED");
        JsonNode closed = json(postJson(session, "/api/v1/service-feedback/" + feedbackId + "/handle", """
                {"action":"CLOSE","content":"店长复核后关闭"}
                """, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(closed.path("feedback").path("status").asText())
                .isEqualTo("CLOSED");
        org.assertj.core.api.Assertions.assertThat(closed.path("actions").size()).isEqualTo(5);
        JsonNode reopened = json(postJson(session, "/api/v1/service-feedback/" + feedbackId + "/handle", """
                {"action":"REOPEN","handlerId":101,"content":"会员补充反馈，需要再次跟进"}
                """, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(reopened.path("feedback").path("status").asText())
                .isEqualTo("PROCESSING");
        org.assertj.core.api.Assertions.assertThat(reopened.path("feedback").path("dueHours").asInt())
                .isEqualTo(24);
        org.assertj.core.api.Assertions.assertThat(reopened.path("feedback").path("overdue").asBoolean())
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(reopened.path("actions").size()).isEqualTo(6);
    }

    @Test
    void reversalCancelsOnlyThePendingVisitTask() throws Exception {
        MockHttpSession session = login();
        String key = String.valueOf(System.nanoTime());
        JsonNode created = createBill(session, "visit-reversal-bill-" + key);
        long billId = created.path("id").asLong();
        addLine(session, billId, 301, 101, created.path("version").asText());
        settle(session, billId, "visit-reversal-settle-" + key, 168);
        JsonNode task = visitForBill(session, billId);

        JsonNode submitted = json(postJson(session, "/api/v1/bills/" + billId + "/reversals", """
                {"reason":"回访取消联动测试","idempotencyKey":"visit-reversal-request-%s"}
                """.formatted(key), 201)).path("data");
        long reversalId = submitted.path("reversal").path("id").asLong();
        JsonNode approved = json(postJson(session, "/api/v1/reversals/" + reversalId + "/review", """
                {"approved":true,"version":"%s"}
                """.formatted(submitted.path("reversal").path("version").asText()), 200)).path("data");
        postJson(session, "/api/v1/reversals/" + reversalId + "/execute", """
                {"version":"%s","idempotencyKey":"visit-reversal-execute-%s"}
                """.formatted(approved.path("reversal").path("version").asText(), key), 200);

        JsonNode cancelled = json(getJson(session, "/api/v1/visit-tasks/" + task.path("id").asLong(), 200))
                .path("data").path("task");
        org.assertj.core.api.Assertions.assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        org.assertj.core.api.Assertions.assertThat(cancelled.path("cancelReason").asText()).contains("整单冲销");
    }

    @Test
    void complaintWithoutSatisfactionDoesNotInventAScore() throws Exception {
        MockHttpSession session = login();
        String key = String.valueOf(System.nanoTime());
        JsonNode created = createBill(session, "unscored-feedback-bill-" + key);
        long billId = created.path("id").asLong();
        addLine(session, billId, 301, 101, created.path("version").asText());
        settle(session, billId, "unscored-feedback-settle-" + key, 168);
        JsonNode task = visitForBill(session, billId);

        postJson(session, "/api/v1/visit-tasks/" + task.path("id").asLong() + "/records", """
                {"employeeId":101,"resultCode":"DECLINED","complaintFlag":true,
                 "content":"会员拒绝评分，但明确要求店长处理服务问题"}
                """, 200);

        JsonNode feedback = feedbackForBill(session, billId);
        org.assertj.core.api.Assertions.assertThat(feedback).isNotNull();
        org.assertj.core.api.Assertions.assertThat(feedback.has("score")).isFalse();
        org.assertj.core.api.Assertions.assertThat(feedback.path("status").asText()).isEqualTo("OPEN");
    }

    private JsonNode visitForBill(MockHttpSession session, long billId) throws Exception {
        JsonNode tasks = json(getJson(session, "/api/v1/visit-tasks", 200)).path("data");
        for (JsonNode item : tasks) if (item.path("billId").asLong() == billId) return item;
        throw new AssertionError("结算后未生成回访任务");
    }

    private JsonNode feedbackForBill(MockHttpSession session, long billId) throws Exception {
        JsonNode items = json(getJson(session, "/api/v1/service-feedback", 200)).path("data");
        for (JsonNode item : items) if (item.path("billId").asLong() == billId) return item;
        return null;
    }

    private JsonNode createBill(MockHttpSession session, String key) throws Exception {
        return json(postJson(session, "/api/v1/bills", """
                {"memberId":1001,"storeId":2,"sourceType":"PC","personCount":1,
                 "idempotencyKey":"%s"}
                """.formatted(key), 201)).path("data");
    }

    private JsonNode addLine(
            MockHttpSession session, long billId, long serviceId, long employeeId, String version) throws Exception {
        return json(postJson(session, "/api/v1/bills/" + billId + "/lines", """
                {"serviceId":%d,"quantity":1,"employeeId":%d,"version":"%s"}
                """.formatted(serviceId, employeeId, version), 200)).path("data");
    }

    private void settle(MockHttpSession session, long billId, String key, int amount) throws Exception {
        JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                {"payments":[{"paymentMethodId":1,"amount":%d}]}
                """.formatted(amount), 200)).path("data");
        String settleBody = """
                {"quoteNo":"%s","idempotencyKey":"%s"}
                """.formatted(quote.path("quoteNo").asText(), key);
        postJson(session, "/api/v1/bills/" + billId + "/settle", settleBody, 200);
        postJson(session, "/api/v1/bills/" + billId + "/settle", settleBody, 200);
    }

    private String getJson(MockHttpSession session, String url, int code) throws Exception {
        return mockMvc.perform(get(url).session(session)).andExpect(status().is(code))
                .andReturn().getResponse().getContentAsString();
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
