package com.yuezhijian.server;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class MasterDataFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanMaintainAppointmentMasterData() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/v1/positions").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
        mockMvc.perform(get("/api/v1/item-categories").param("type", "SERVICE").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())));

        mockMvc.perform(post("/api/v1/employees")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeNo":"E-TEST-901",
                                  "name":"预约测试技师",
                                  "mobile":"13912349001",
                                  "positionId":1,
                                  "primaryStoreId":2,
                                  "canService":true,
                                  "canSell":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber());
        mockMvc.perform(get("/api/v1/employees").param("keyword", "E-TEST-901").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].maskedMobile").value("*******9001"));

        mockMvc.perform(post("/api/v1/workstations")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":2,"code":"W-TEST-901","name":"测试工位","capacity":1,"sortNo":90}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/workstations").param("storeId", "2").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())));

        mockMvc.perform(post("/api/v1/services")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"SVC-TEST-901",
                                  "name":"自动化测试服务",
                                  "categoryId":1,
                                  "durationMinutes":90,
                                  "costAmount":20,
                                  "listPrice":199,
                                  "storePrice":169,
                                  "storeIds":[2],
                                  "description":"用于验证预约前置数据"
                                }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/services")
                        .param("storeId", "2").param("keyword", "SVC-TEST-901").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].storePrice").value(169));
    }

    @Test
    void masterDataCreationRejectsInvalidReferencesAndValues() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/v1/workstations")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":9999,"code":"INVALID","name":"无效门店工位","capacity":1,"sortNo":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40002"));
        mockMvc.perform(post("/api/v1/services")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"INVALID-COST","name":"成本错误服务","categoryId":1,
                                  "durationMinutes":60,"costAmount":200,"listPrice":100,
                                  "storePrice":100,"storeIds":[2]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40002"));
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
