package com.yuezhijian.server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.member.OwnershipAdjustmentService;
import java.time.LocalDate;
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
class OwnershipAdjustmentFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OwnershipAdjustmentService ownershipService;

    @Test
    void sameDayApprovalChangesOnlyCurrentOwnershipAndClearsOldAdvisor() throws Exception {
        MockHttpSession session = login();
        JsonNode member = createMember(session, "13398761001", 101L);
        long memberId = member.path("id").asLong();
        String request = """
                {
                  "newStoreId":1,
                  "effectiveDate":"%s",
                  "shareRule":{"ruleRef":"CUSTOMER-CONFIRMED-LATER"},
                  "reason":"客户长期转至总部服务",
                  "memberVersion":"%s"
                }
                """.formatted(LocalDate.now(), member.path("version").asText());

        JsonNode adjustment = json(postJson(
                session, "/api/v1/members/" + memberId + "/ownership-adjustments", request, 201)).path("data");
        org.assertj.core.api.Assertions.assertThat(adjustment.path("approvalStatus").asText()).isEqualTo("PENDING");
        org.assertj.core.api.Assertions.assertThat(adjustment.path("executionStatus").asText()).isEqualTo("WAITING");
        org.assertj.core.api.Assertions.assertThat(adjustment.path("shareRule").path("ruleRef").asText())
                .isEqualTo("CUSTOMER-CONFIRMED-LATER");

        postJson(session, "/api/v1/members/" + memberId + "/ownership-adjustments", request, 409);
        mockMvc.perform(get("/api/v1/ownership-adjustments").param("memberId", String.valueOf(memberId))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].adjustmentNo").value(adjustment.path("adjustmentNo").asText()));

        JsonNode approved = json(postJson(
                session, "/api/v1/ownership-adjustments/" + adjustment.path("id").asLong() + "/approve", """
                        {"comment":"同意按申请日期转店","version":"%s"}
                        """.formatted(adjustment.path("version").asText()), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(approved.path("approvalStatus").asText()).isEqualTo("APPROVED");
        org.assertj.core.api.Assertions.assertThat(approved.path("executionStatus").asText()).isEqualTo("APPLIED");
        org.assertj.core.api.Assertions.assertThat(approved.path("executionMessage").asText())
                .contains("历史单据保持原归属");

        JsonNode moved = member(session, memberId);
        org.assertj.core.api.Assertions.assertThat(moved.path("ownerStoreId").asLong()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(moved.path("advisorEmployeeId").isMissingNode()
                || moved.path("advisorEmployeeId").isNull()).isTrue();
    }

    @Test
    void futureRequestWaitsForEffectiveDateAndRejectedRequestReleasesMember() throws Exception {
        MockHttpSession session = login();
        JsonNode member = createMember(session, "13398761002", null);
        long memberId = member.path("id").asLong();
        String request = """
                {
                  "newStoreId":1,
                  "effectiveDate":"%s",
                  "shareRule":{},
                  "reason":"计划下周转店",
                  "memberVersion":"%s"
                }
                """.formatted(LocalDate.now().plusDays(1), member.path("version").asText());
        JsonNode adjustment = json(postJson(
                session, "/api/v1/members/" + memberId + "/ownership-adjustments", request, 201)).path("data");

        postJson(session, "/api/v1/ownership-adjustments/" + adjustment.path("id").asLong() + "/reject", """
                {"comment":"","version":"%s"}
                """.formatted(adjustment.path("version").asText()), 400);
        JsonNode rejected = json(postJson(
                session, "/api/v1/ownership-adjustments/" + adjustment.path("id").asLong() + "/reject", """
                        {"comment":"门店与客户尚未确认","version":"%s"}
                        """.formatted(adjustment.path("version").asText()), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(rejected.path("approvalStatus").asText()).isEqualTo("REJECTED");
        org.assertj.core.api.Assertions.assertThat(rejected.path("executionStatus").asText()).isEqualTo("CANCELLED");
        org.assertj.core.api.Assertions.assertThat(member(session, memberId).path("ownerStoreId").asLong()).isEqualTo(2L);

        postJson(session, "/api/v1/members/" + memberId + "/ownership-adjustments", request, 201);
    }

    @Test
    void approvedFutureRequestIsAppliedOnlyWhenBusinessDateArrives() throws Exception {
        MockHttpSession session = login();
        JsonNode member = createMember(session, "13398761003", null);
        long memberId = member.path("id").asLong();
        LocalDate effectiveDate = LocalDate.now().plusDays(2);
        JsonNode adjustment = json(postJson(
                session, "/api/v1/members/" + memberId + "/ownership-adjustments", """
                        {
                          "newStoreId":1,
                          "effectiveDate":"%s",
                          "shareRule":{},
                          "reason":"未来日期生效验证",
                          "memberVersion":"%s"
                        }
                        """.formatted(effectiveDate, member.path("version").asText()), 201)).path("data");
        JsonNode approved = json(postJson(
                session, "/api/v1/ownership-adjustments/" + adjustment.path("id").asLong() + "/approve", """
                        {"comment":"同意未来生效","version":"%s"}
                        """.formatted(adjustment.path("version").asText()), 200)).path("data");
        org.assertj.core.api.Assertions.assertThat(approved.path("executionStatus").asText()).isEqualTo("WAITING");
        org.assertj.core.api.Assertions.assertThat(member(session, memberId).path("ownerStoreId").asLong()).isEqualTo(2L);

        org.assertj.core.api.Assertions.assertThat(ownershipService.applyDueOwnershipChanges(effectiveDate)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(member(session, memberId).path("ownerStoreId").asLong()).isEqualTo(1L);
    }

    private JsonNode createMember(MockHttpSession session, String mobile, Long advisorId) throws Exception {
        String advisor = advisorId == null ? "" : ",\"advisorEmployeeId\":" + advisorId;
        long memberId = json(postJson(session, "/api/v1/members", """
                {"fullName":"归属测试会员","mobile":"%s","joinStoreId":2,"ownerStoreId":2%s}
                """.formatted(mobile, advisor), 201)).path("data").path("memberId").asLong();
        return member(session, memberId);
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

    private JsonNode json(String value) throws Exception { return objectMapper.readTree(value); }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"TestPassword!2026\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
