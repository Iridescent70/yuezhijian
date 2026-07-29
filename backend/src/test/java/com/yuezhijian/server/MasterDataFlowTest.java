package com.yuezhijian.server;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
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
class MasterDataFlowTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void serviceCanBeEditedAndRejectsStaleVersion() throws Exception {
        MockHttpSession session = login();
        String createdJson = mockMvc.perform(post("/api/v1/services")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"SVC-EDIT-902","name":"待编辑服务","categoryId":1,
                                  "durationMinutes":60,"costAmount":20,"listPrice":180,
                                  "storePrice":160,"storeIds":[2],"description":"初始说明"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createdJson).path("data").path("id").asLong();
        String detailJson = mockMvc.perform(get("/api/v1/services/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stores[0].storeId").value(2))
                .andReturn().getResponse().getContentAsString();
        JsonNode detail = objectMapper.readTree(detailJson).path("data");
        String version = detail.path("version").asText();
        String update = """
                {
                  "name":"已编辑服务","categoryId":1,"durationMinutes":90,
                  "costAmount":25,"listPrice":200,"storeId":2,"storePrice":188,
                  "saleStatus":"OFF_SALE","status":"ACTIVE","description":"更新说明",
                  "version":"%s"
                }
                """.formatted(version);
        mockMvc.perform(put("/api/v1/services/{id}", id)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("已编辑服务"))
                .andExpect(jsonPath("$.data.stores[0].storePrice").value(188))
                .andExpect(jsonPath("$.data.stores[0].saleStatus").value("OFF_SALE"));

        mockMvc.perform(put("/api/v1/services/{id}", id)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict());
    }

    @Test
    void employeeAndWorkstationCanBeEditedAndRejectStaleVersions() throws Exception {
        MockHttpSession session = login();
        String employeeCreated = mockMvc.perform(post("/api/v1/employees")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeNo":"E-EDIT-903","name":"待编辑员工","mobile":"13912349003",
                                  "positionId":1,"primaryStoreId":2,"hireDate":"2026-07-01",
                                  "canService":true,"canSell":false
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long employeeId = objectMapper.readTree(employeeCreated).path("data").path("id").asLong();
        String employeeDetail = mockMvc.perform(get("/api/v1/employees/{id}", employeeId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedMobile").value("*******9003"))
                .andReturn().getResponse().getContentAsString();
        String employeeVersion = objectMapper.readTree(employeeDetail).path("data").path("version").asText();
        String employeeUpdate = """
                {
                  "name":"已离职员工","positionId":1,"primaryStoreId":2,
                  "hireDate":"2026-07-01","leaveDate":"2026-07-30",
                  "canService":false,"canSell":false,"status":"LEFT","version":"%s"
                }
                """.formatted(employeeVersion);
        mockMvc.perform(put("/api/v1/employees/{id}", employeeId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(employeeUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("已离职员工"))
                .andExpect(jsonPath("$.data.status").value("LEFT"))
                .andExpect(jsonPath("$.data.leaveDate").value("2026-07-30"))
                .andExpect(jsonPath("$.data.maskedMobile").value("*******9003"));
        mockMvc.perform(put("/api/v1/employees/{id}", employeeId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(employeeUpdate))
                .andExpect(status().isConflict());

        String workstationCreated = mockMvc.perform(post("/api/v1/workstations")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":2,"code":"W-EDIT-903","name":"待编辑工位","capacity":1,"sortNo":93}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long workstationId = objectMapper.readTree(workstationCreated).path("data").path("id").asLong();
        String workstationDetail = mockMvc.perform(get("/api/v1/workstations/{id}", workstationId)
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String workstationVersion = objectMapper.readTree(workstationDetail).path("data").path("version").asText();
        String workstationUpdate = """
                {"name":"已停用工位","capacity":2,"sortNo":94,"status":"DISABLED","version":"%s"}
                """.formatted(workstationVersion);
        mockMvc.perform(put("/api/v1/workstations/{id}", workstationId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(workstationUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("已停用工位"))
                .andExpect(jsonPath("$.data.capacity").value(2))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(put("/api/v1/workstations/{id}", workstationId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(workstationUpdate))
                .andExpect(status().isConflict());
    }

    @Test
    void positionCanBeCreatedEditedDisabledAndFiltered() throws Exception {
        MockHttpSession session = login();
        String created = mockMvc.perform(post("/api/v1/positions")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"NAIL_ASSISTANT","name":"美甲助理","level":6,
                                  "defaultServiceRate":0.050000,"defaultSalesRate":0.030000
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").path("id").asLong();
        String detailJson = mockMvc.perform(get("/api/v1/positions/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("NAIL_ASSISTANT"))
                .andExpect(jsonPath("$.data.defaultServiceRate").value(0.05))
                .andReturn().getResponse().getContentAsString();
        String version = objectMapper.readTree(detailJson).path("data").path("version").asText();
        String update = """
                {
                  "name":"停用助理","level":7,"defaultServiceRate":0.060000,
                  "defaultSalesRate":0.040000,"status":"DISABLED","version":"%s"
                }
                """.formatted(version);
        mockMvc.perform(put("/api/v1/positions/{id}", id)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("停用助理"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(get("/api/v1/positions").param("activeOnly", "true").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(id), hasSize(0)));
        mockMvc.perform(get("/api/v1/positions").param("activeOnly", "false").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(id), hasSize(1)));
        mockMvc.perform(put("/api/v1/positions/{id}", id)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict());
    }

    @Test
    void categoryAndUnitCanBeCreatedEditedDisabledAndFiltered() throws Exception {
        MockHttpSession session = login();

        String categoryCreated = mockMvc.perform(post("/api/v1/item-categories")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PRODUCT","code":"NAIL_TOOL","name":"美甲工具","sortNo":30}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long categoryId = objectMapper.readTree(categoryCreated).path("data").path("id").asLong();
        String categoryDetail = mockMvc.perform(get("/api/v1/item-categories/{id}", categoryId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("PRODUCT"))
                .andExpect(jsonPath("$.data.code").value("NAIL_TOOL"))
                .andReturn().getResponse().getContentAsString();
        String categoryVersion = objectMapper.readTree(categoryDetail).path("data").path("version").asText();
        String categoryUpdate = """
                {"name":"停用工具分类","sortNo":35,"status":"DISABLED","version":"%s"}
                """.formatted(categoryVersion);
        mockMvc.perform(put("/api/v1/item-categories/{id}", categoryId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(categoryUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortNo").value(35))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(get("/api/v1/item-categories")
                        .param("type", "PRODUCT").param("activeOnly", "true").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(categoryId), hasSize(0)));
        mockMvc.perform(get("/api/v1/item-categories")
                        .param("type", "PRODUCT").param("activeOnly", "false").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(categoryId), hasSize(1)));
        mockMvc.perform(put("/api/v1/item-categories/{id}", categoryId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(categoryUpdate))
                .andExpect(status().isConflict());

        String unitCreated = mockMvc.perform(post("/api/v1/units")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"GRAM","name":"克","decimalPlaces":2}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long unitId = objectMapper.readTree(unitCreated).path("data").path("id").asLong();
        String unitDetail = mockMvc.perform(get("/api/v1/units/{id}", unitId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("GRAM"))
                .andReturn().getResponse().getContentAsString();
        String unitVersion = objectMapper.readTree(unitDetail).path("data").path("version").asText();
        String unitUpdate = """
                {"name":"克（停用）","decimalPlaces":3,"status":"DISABLED","version":"%s"}
                """.formatted(unitVersion);
        mockMvc.perform(put("/api/v1/units/{id}", unitId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(unitUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decimalPlaces").value(3))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(get("/api/v1/units").param("activeOnly", "true").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(unitId), hasSize(0)));
        mockMvc.perform(get("/api/v1/units").param("activeOnly", "false").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(unitId), hasSize(1)));
        mockMvc.perform(put("/api/v1/units/{id}", unitId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(unitUpdate))
                .andExpect(status().isConflict());
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
