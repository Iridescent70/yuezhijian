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
class ServiceAreaFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void serviceAreaCanBeCreatedUpdatedFilteredAndAudited() throws Exception {
        MockHttpSession session = login();
        JsonNode created = data(mockMvc.perform(post("/api/v1/service-areas").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "storeId":2,"city":"上海市","district":"浦东新区",
                                  "address":"张江自动化服务区946","longitude":121.6001000,
                                  "latitude":31.2011000,"radiusKm":6.500,"visitFee":38.0000
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.storeId").value(2))
                .andExpect(jsonPath("$.data.updatedByName").value("本地管理员"))
                .andReturn().getResponse().getContentAsString());
        long id = created.path("id").asLong();
        String version = created.path("version").asText();

        mockMvc.perform(get("/api/v1/service-areas").session(session)
                        .param("storeId", "2").param("keyword", "张江").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].radiusKm").value(6.5));

        String updateBody = """
                {
                  "city":"上海市","district":"浦东新区","address":"张江科学城服务区946",
                  "longitude":121.6011000,"latitude":31.2021000,"radiusKm":8.000,
                  "visitFee":45.0000,"status":"DISABLED","version":"%s"
                }
                """.formatted(version);
        mockMvc.perform(put("/api/v1/service-areas/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("张江科学城服务区946"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(put("/api/v1/service-areas/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40901"));

        mockMvc.perform(post("/api/v1/service-areas").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "storeId":2,"city":"上海市","district":"浦东新区",
                                  "address":"张江科学城服务区946","longitude":121.7000000,
                                  "latitude":31.3000000,"radiusKm":5.000,"visitFee":30.0000
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40901"));

        mockMvc.perform(get("/api/v1/audit-logs").session(session)
                        .param("objectType", "SERVICE_AREA").param("objectId", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
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
