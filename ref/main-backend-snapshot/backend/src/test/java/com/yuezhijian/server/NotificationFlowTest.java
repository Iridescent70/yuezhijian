package com.yuezhijian.server;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
class NotificationFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void announcementIsScopedPublishedReadAndConcurrencyProtected() throws Exception {
        MockHttpSession session = login();
        JsonNode created = data(postJson(session, "/api/v1/announcements", """
                {
                  "title":"门店营业通知","body":"今晚闭店前请完成日结。","scopeType":"STORES",
                  "storeIds":[1,1],"priority":80,"pinned":true,"status":"PUBLISHED"
                }
                """, 201));
        long id = created.path("id").asLong();
        String version = created.path("version").asText();

        mockMvc.perform(get("/api/v1/notifications").session(session)
                        .param("messageType", "ANNOUNCEMENT").param("readStatus", "UNREAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].title", hasItem("门店营业通知")))
                .andExpect(jsonPath("$.data.items[?(@.id == %d)].pinned".formatted(id), hasItem(true)));
        mockMvc.perform(post("/api/v1/notifications/{id}/read", id).with(csrf()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.read").value(true));
        mockMvc.perform(post("/api/v1/notifications/{id}/read", id).with(csrf()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.read").value(true));

        mockMvc.perform(put("/api/v1/announcements/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "title":"非法退回草稿","body":"不能退回","scopeType":"STORES",
                                  "storeIds":[1],"priority":80,"pinned":true,"status":"DRAFT",
                                  "version":"%s"
                                }
                                """.formatted(version)))
                .andExpect(status().isBadRequest());
        JsonNode disabled = data(putJson(session, "/api/v1/announcements/" + id, """
                {
                  "title":"门店营业通知","body":"今晚闭店前请完成日结。","scopeType":"STORES",
                  "storeIds":[1],"priority":80,"pinned":true,"status":"DISABLED","version":"%s"
                }
                """.formatted(version), 200));
        mockMvc.perform(put("/api/v1/announcements/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "title":"过期更新","body":"过期版本","scopeType":"STORES",
                                  "storeIds":[1],"priority":80,"pinned":true,"status":"DISABLED",
                                  "version":"%s"
                                }
                                """.formatted(version)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/notifications/{id}", id).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/audit-logs").session(session)
                        .param("objectType", "ANNOUNCEMENT").param("objectId", String.valueOf(id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(2)));
        org.assertj.core.api.Assertions.assertThat(disabled.path("storeIds").size()).isEqualTo(1);
    }

    @Test
    void futureAnnouncementStaysHiddenAndTemplateTestRendersDeclaredVariables() throws Exception {
        MockHttpSession session = login();
        String from = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS).toString();
        JsonNode future = data(postJson(session, "/api/v1/announcements", """
                {
                  "title":"明日公告","body":"明日才显示","scopeType":"ALL","storeIds":[],
                  "validFrom":"%s","priority":0,"pinned":false,"status":"PUBLISHED"
                }
                """.formatted(from), 201));
        mockMvc.perform(get("/api/v1/notifications/{id}", future.path("id").asLong()).session(session))
                .andExpect(status().isNotFound());

        JsonNode template = data(postJson(session, "/api/v1/notification-templates", """
                {
                  "eventCode":"CUSTOM_GREETING","eventName":"自定义问候",
                  "titleTemplate":"您好，{{memberName}}",
                  "bodyTemplate":"{{memberName}}，欢迎来到{{storeName}}。",
                  "variables":["memberName","storeName"],"status":"ACTIVE"
                }
                """, 201));
        mockMvc.perform(post("/api/v1/notification-templates").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "eventCode":"INVALID_PLACEHOLDER","eventName":"错误模板",
                                  "titleTemplate":"{{missing}}","bodyTemplate":"正文",
                                  "variables":[],"status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("模板使用了未声明变量：missing"));
        JsonNode sent = data(postJson(session, "/api/v1/notifications/test", """
                {"templateId":%d,"variables":{"memberName":"林女士","storeName":"悦指间总部"}}
                """.formatted(template.path("id").asLong()), 200));
        org.assertj.core.api.Assertions.assertThat(sent.path("title").asText()).isEqualTo("您好，林女士");
        mockMvc.perform(get("/api/v1/notifications").session(session).param("messageType", "SYSTEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].notificationNo", hasItem(sent.path("notificationNo").asText())))
                .andExpect(jsonPath("$.data.items[*].body", hasItem("林女士，欢迎来到悦指间总部。")));
    }

    private String postJson(MockHttpSession session, String url, String body, int statusCode) throws Exception {
        return mockMvc.perform(post(url).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(statusCode)).andReturn().getResponse().getContentAsString();
    }

    private String putJson(MockHttpSession session, String url, String body, int statusCode) throws Exception {
        return mockMvc.perform(put(url).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(statusCode)).andReturn().getResponse().getContentAsString();
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
