package com.yuezhijian.server;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
class MemberBatchFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void batchFreezeReturnsSuccessSkipAndFailureWithoutDuplicatingMembers() throws Exception {
        MockHttpSession session = login();
        long activeId = createMember(session, "批量冻结测试", "13592000001", 2L);

        mockMvc.perform(post("/api/v1/members/batch-freeze")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[null],\"reason\":\"无效请求\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40001"));

        mockMvc.perform(post("/api/v1/members/batch-freeze")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "memberIds", List.of(activeId, activeId, 1003L, 999999L),
                                "reason", "批量清理异常账户"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operation").value("FREEZE"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.succeeded").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.items[2].status").value("FAILED"));

        mockMvc.perform(get("/api/v1/members/{id}", activeId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"))
                .andExpect(jsonPath("$.data.freezeReason").value("批量清理异常账户"));
    }

    @Test
    void batchTagsOnlyWritesEffectiveChangesAndThenSkipsSameTarget() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session, "批量标签测试", "13592000002", 2L);
        Map<String, Object> payload = Map.of(
                "memberIds", List.of(memberId), "addIds", List.of(3L), "removeIds", List.of(1L));

        mockMvc.perform(post("/api/v1/members/tags/batch")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeeded").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        mockMvc.perform(get("/api/v1/members/{id}", memberId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags", hasSize(1)))
                .andExpect(jsonPath("$.data.tags[0].code").value("FOLLOW_UP"));

        mockMvc.perform(post("/api/v1/members/tags/batch")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeeded").value(0))
                .andExpect(jsonPath("$.data.skipped").value(1));
    }

    @Test
    void batchAdvisorKeepsStoreValidationAndReturnsPerMemberOutcome() throws Exception {
        MockHttpSession session = login();
        long storeTwoA = createMember(session, "批量顾问甲", "13592000003", 2L);
        long storeTwoB = createMember(session, "批量顾问乙", "13592000004", 2L);
        long storeOne = createMember(session, "批量顾问跨店", "13592000005", 1L);

        mockMvc.perform(post("/api/v1/members/batch-assign-advisor")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "memberIds", List.of(storeTwoA, storeTwoB, storeOne), "employeeId", 101L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeeded").value(2))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.items[2].message").value("所选顾问不存在、已停用或不属于归属门店"));

        mockMvc.perform(get("/api/v1/members/{id}", storeTwoA).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.advisorEmployeeId").value(101));
    }

    private long createMember(MockHttpSession session, String name, String mobile, long storeId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/members")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", name,
                                "mobile", mobile,
                                "gender", "FEMALE",
                                "sourceType", "MANUAL",
                                "joinStoreId", storeId,
                                "ownerStoreId", storeId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return body.path("data").path("memberId").asLong();
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-admin","password":"TestPassword!2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
