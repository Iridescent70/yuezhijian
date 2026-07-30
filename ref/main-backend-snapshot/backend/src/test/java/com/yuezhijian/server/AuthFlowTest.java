package com.yuezhijian.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.profiles.active=memory",
        "app.bootstrap.username=test-admin",
        "app.bootstrap.password=TestPassword!2026"
})
@AutoConfigureMockMvc
class AuthFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40101"));
    }

    @Test
    void loginCreatesSessionAndAllowsAuthorizedQueries() throws Exception {
        HttpSession session = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-admin","password":"TestPassword!2026"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("test-admin"))
                .andExpect(jsonPath("$.data.permissions[0]").exists())
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(get("/api/v1/auth/me").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStoreName").value("悦指间总部"));

        mockMvc.perform(get("/api/v1/stores").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/v1/workbench/overview")
                        .session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revenue").value(0));
    }

    @Test
    void wrongPasswordReturnsUnifiedError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-admin","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40101"));
    }

    @Test
    void headquartersUserCanSwitchStoreWithinTheCurrentSession() throws Exception {
        var session = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-admin","password":"TestPassword!2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(post("/api/v1/auth/current-store")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStoreId").value(2))
                .andExpect(jsonPath("$.data.currentStoreName").value("悦指间示范店"));

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStoreId").value(2));

        var anotherSession = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-admin","password":"TestPassword!2026"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStoreId").value(1))
                .andReturn()
                .getRequest()
                .getSession(false);
        assertThat(anotherSession.getId()).isNotEqualTo(session.getId());

        mockMvc.perform(post("/api/v1/auth/current-store")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40002"));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-admin","password":"TestPassword!2026"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    @Test
    void logoutInvalidatesCurrentSession() throws Exception {
        var session = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-admin","password":"TestPassword!2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
                .andExpect(status().isOk());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
