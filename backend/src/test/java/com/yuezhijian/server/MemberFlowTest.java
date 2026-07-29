package com.yuezhijian.server;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class MemberFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void memberListSupportsSearchAndNeverReturnsFullMobile() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/v1/members")
                        .param("keyword", "林晓悦")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items[0].fullName").value("林晓悦"))
                .andExpect(jsonPath("$.data.items[0].maskedMobile").value("*******1001"))
                .andExpect(content().string(not(containsString("13800001001"))));
    }

    @Test
    void createMemberBuildsAssetsAndCanBeReadBack() throws Exception {
        MockHttpSession session = login();
        String response = mockMvc.perform(post("/api/v1/members")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "自动化测试会员",
                                  "mobile": "13612345678",
                                  "gender": "FEMALE",
                                  "birthday": "1995-08-18",
                                  "sourceType": "MANUAL",
                                  "joinStoreId": 2,
                                  "ownerStoreId": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberNo").exists())
                .andExpect(jsonPath("$.data.membershipCardNo").exists())
                .andReturn().getResponse().getContentAsString();

        long memberId = objectMapper.readTree(response).path("data").path("memberId").asLong();
        mockMvc.perform(get("/api/v1/members/{id}", memberId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("自动化测试会员"))
                .andExpect(jsonPath("$.data.maskedMobile").value("*******5678"))
                .andExpect(jsonPath("$.data.assets.availableBalance").value(0))
                .andExpect(jsonPath("$.data.assets.availablePoints").value(0))
                .andExpect(jsonPath("$.data.tags[0].code").value("NEW_MEMBER"));

        mockMvc.perform(post("/api/v1/members")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "重复手机号",
                                  "mobile": "13612345678",
                                  "joinStoreId": 2
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40901"));
    }

    @Test
    void invalidStoreAndMissingMemberReturnBusinessErrors() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/v1/members")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"错误门店会员","mobile":"13512345679","joinStoreId":9999}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40002"));

        mockMvc.perform(get("/api/v1/members/999999").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("40401"));
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
