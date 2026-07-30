package com.yuezhijian.server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class MemberManagementFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void editStatusAndManualTagsPreserveVersionsAndHistorySemantics() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session);
        JsonNode member = member(session, memberId);
        String firstVersion = member.path("version").asText();

        JsonNode updated = json(putJson(session, "/api/v1/members/" + memberId, """
                {
                  "fullName":"资料维护会员",
                  "nickname":"维护样例",
                  "mobile":"13498760021",
                  "gender":"OTHER",
                  "birthday":"1990-01-02",
                  "email":"member@example.com",
                  "special":true,
                  "version":"%s"
                }
                """.formatted(firstVersion), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(updated.path("fullName").asText()).isEqualTo("资料维护会员");
        org.assertj.core.api.Assertions.assertThat(updated.path("maskedMobile").asText()).isEqualTo("*******0021");
        org.assertj.core.api.Assertions.assertThat(updated.path("special").asBoolean()).isTrue();

        putJson(session, "/api/v1/members/" + memberId, """
                {
                  "fullName":"过期版本覆盖",
                  "gender":"OTHER",
                  "special":true,
                  "version":"%s"
                }
                """.formatted(firstVersion), 409);

        JsonNode frozen = json(postJson(session, "/api/v1/members/" + memberId + "/status", """
                {"status":"FROZEN","reason":"客户主动要求暂停服务","version":"%s"}
                """.formatted(updated.path("version").asText()), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(frozen.path("status").asText()).isEqualTo("FROZEN");
        org.assertj.core.api.Assertions.assertThat(frozen.path("freezeReason").asText()).isEqualTo("客户主动要求暂停服务");
        org.assertj.core.api.Assertions.assertThat(frozen.path("frozenAt").asText()).isNotBlank();

        JsonNode active = json(postJson(session, "/api/v1/members/" + memberId + "/status", """
                {"status":"ACTIVE","reason":"客户确认恢复服务","version":"%s"}
                """.formatted(frozen.path("version").asText()), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(active.path("status").asText()).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(active.path("freezeReason").isMissingNode()
                || active.path("freezeReason").isNull()).isTrue();

        mockMvc.perform(get("/api/v1/member-tags").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'FOLLOW_UP')]").exists());

        JsonNode tagged = json(putJson(session, "/api/v1/members/" + memberId + "/tags", """
                {"addIds":[3],"removeIds":[1],"version":"%s"}
                """.formatted(active.path("version").asText()), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(tagged.path("tags").size()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(tagged.path("tags").get(0).path("code").asText())
                .isEqualTo("FOLLOW_UP");

        putJson(session, "/api/v1/members/" + memberId + "/tags", """
                {"addIds":[999],"removeIds":[],"version":"%s"}
                """.formatted(tagged.path("version").asText()), 400);
    }

    private long createMember(MockHttpSession session) throws Exception {
        return json(postJson(session, "/api/v1/members", """
                {
                  "fullName":"待维护会员",
                  "mobile":"13498760020",
                  "gender":"FEMALE",
                  "joinStoreId":2,
                  "ownerStoreId":2
                }
                """, 201)).path("data").path("memberId").asLong();
    }

    private JsonNode member(MockHttpSession session, long id) throws Exception {
        String response = mockMvc.perform(get("/api/v1/members/{id}", id).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json(response).path("data");
    }

    private String postJson(MockHttpSession session, String path, String body, int expected) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private String putJson(MockHttpSession session, String path, String body, int expected) throws Exception {
        return mockMvc.perform(put(path).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String value) throws Exception { return objectMapper.readTree(value); }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"TestPassword!2026\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
