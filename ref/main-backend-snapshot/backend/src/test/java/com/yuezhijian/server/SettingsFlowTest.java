package com.yuezhijian.server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.settings.SystemSettingsService;
import java.time.Duration;
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
class SettingsFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SystemSettingsService settings;

    @Test
    void visitDueHoursParameterControlsNewTasksAndCanBeRestored() throws Exception {
        MockHttpSession session = login();
        JsonNode parameter = visitDueParameter(session);
        JsonNode changed = json(putJson(session, "/api/v1/system-parameters/" + parameter.path("id").asLong(), """
                {"value":"48","status":"ACTIVE","version":"%s"}
                """.formatted(parameter.path("version").asText()), 200)).path("data");
        JsonNode latest = changed;
        try {
            String key = String.valueOf(System.nanoTime());
            JsonNode created = json(postJson(session, "/api/v1/bills", """
                    {"memberId":1001,"storeId":2,"sourceType":"PC","personCount":1,
                     "idempotencyKey":"settings-bill-%s"}
                    """.formatted(key), 201)).path("data");
            long billId = created.path("id").asLong();
            postJson(session, "/api/v1/bills/" + billId + "/lines", """
                    {"serviceId":301,"quantity":1,"employeeId":101,"version":"%s"}
                    """.formatted(created.path("version").asText()), 200);
            JsonNode quote = json(postJson(session, "/api/v1/bills/" + billId + "/settlement/quote", """
                    {"payments":[{"paymentMethodId":1,"amount":168}]}
                    """, 200)).path("data");
            postJson(session, "/api/v1/bills/" + billId + "/settle", """
                    {"quoteNo":"%s","idempotencyKey":"settings-settle-%s"}
                    """.formatted(quote.path("quoteNo").asText(), key), 200);

            JsonNode tasks = json(getJson(session, "/api/v1/visit-tasks?storeId=2", 200)).path("data");
            JsonNode task = null;
            for (JsonNode item : tasks) if (item.path("billId").asLong() == billId) task = item;
            org.assertj.core.api.Assertions.assertThat(task).isNotNull();
            LocalDateTime settledAt = LocalDateTime.parse(task.path("settledAt").asText());
            LocalDateTime dueAt = LocalDateTime.parse(task.path("dueAt").asText());
            org.assertj.core.api.Assertions.assertThat(Duration.between(settledAt, dueAt).toHours()).isEqualTo(48);
            latest = json(putJson(session, "/api/v1/system-parameters/" + changed.path("id").asLong(), """
                    {"value":"48","status":"DISABLED","version":"%s"}
                    """.formatted(changed.path("version").asText()), 200)).path("data");
            org.assertj.core.api.Assertions.assertThat(settings.integerValue(
                    "VISIT", "AFTER_SALE_DUE_HOURS", 24, 1, 720)).isEqualTo(24);
        } finally {
            putJson(session, "/api/v1/system-parameters/" + latest.path("id").asLong(), """
                    {"value":"24","status":"ACTIVE","version":"%s"}
                    """.formatted(latest.path("version").asText()), 200);
        }
    }

    @Test
    void satisfactionRuleCanBeMaintainedAndTestedWithoutWritingBusinessData() throws Exception {
        MockHttpSession session = login();
        String name = "负向服务反馈-" + System.nanoTime();
        JsonNode created = json(postJson(session, "/api/v1/satisfaction-rules", """
                {"ruleName":"%s","keywords":["不满意","很差"],"score":1,
                 "componentMapping":{"service":"negative"},"priority":10,"status":"ACTIVE"}
                """.formatted(name), 201)).path("data");

        JsonNode matched = json(postJson(session, "/api/v1/satisfaction-rules/test", """
                {"text":"这次服务很差，希望有人联系我"}
                """, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(matched.path("matched").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(matched.path("ruleId").asLong()).isEqualTo(created.path("id").asLong());
        org.assertj.core.api.Assertions.assertThat(matched.path("score").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(matched.path("componentMapping").path("service").asText())
                .isEqualTo("negative");

        json(putJson(session, "/api/v1/satisfaction-rules/" + created.path("id").asLong(), """
                {"ruleName":"%s","keywords":["不满意","很差"],"score":1,
                 "componentMapping":{"service":"negative"},"priority":10,"status":"DISABLED",
                 "version":"%s"}
                """.formatted(name, created.path("version").asText()), 200));
        JsonNode unmatched = json(postJson(session, "/api/v1/satisfaction-rules/test", """
                {"text":"这次服务很差，希望有人联系我"}
                """, 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(unmatched.path("matched").asBoolean()).isFalse();
        org.assertj.core.api.Assertions.assertThat(unmatched.path("message").asText()).contains("不自动推断");
    }

    private JsonNode visitDueParameter(MockHttpSession session) throws Exception {
        JsonNode items = json(getJson(session, "/api/v1/system-parameters?group=VISIT", 200)).path("data");
        for (JsonNode item : items) {
            if ("AFTER_SALE_DUE_HOURS".equals(item.path("paramKey").asText())) return item;
        }
        throw new AssertionError("未找到回访时限系统参数");
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
