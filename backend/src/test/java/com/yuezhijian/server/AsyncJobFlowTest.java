package com.yuezhijian.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.job.AsyncJobService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.profiles.active=memory",
        "app.bootstrap.username=test-admin",
        "app.bootstrap.password=TestPassword!2026",
        "app.jobs.initial-delay-ms=600000"
})
@AutoConfigureMockMvc
class AsyncJobFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AsyncJobService jobs;

    @Test
    void exportRunsThroughDownloadCenterAndReturnsAPrivateCsv() throws Exception {
        MockHttpSession session = login();
        JsonNode created = json(mockMvc.perform(post("/api/v1/exports").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"SERVICE_FEEDBACK\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("data");
        long id = created.path("id").asLong();
        assertThat(created.path("status").asText()).isEqualTo("PENDING");

        assertThat(jobs.processNext()).isTrue();
        JsonNode detail = json(mockMvc.perform(get("/api/v1/jobs/" + id).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(detail.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(detail.path("resultFileName").asText()).endsWith(".csv");

        byte[] content = mockMvc.perform(get("/api/v1/jobs/" + id + "/result").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(content).startsWith(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
        assertThat(new String(content, StandardCharsets.UTF_8)).contains("反馈编号", "处理时限");

        JsonNode list = json(mockMvc.perform(get("/api/v1/jobs?status=SUCCEEDED").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(list.path("total").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void onlyPendingJobsCanBeCancelled() throws Exception {
        MockHttpSession session = login();
        long id = json(mockMvc.perform(post("/api/v1/exports").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"SERVICE_FEEDBACK\",\"overdue\":true}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString())
                .path("data").path("id").asLong();

        JsonNode cancelled = json(mockMvc.perform(post("/api/v1/jobs/" + id + "/cancel")
                        .with(csrf()).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        mockMvc.perform(get("/api/v1/jobs/" + id + "/result").session(session))
                .andExpect(status().isConflict());
    }

    @Test
    void memberExportUsesTheCurrentStoreAndNeverWritesPlainMobileNumbers() throws Exception {
        MockHttpSession session = login();
        long id = json(mockMvc.perform(post("/api/v1/exports").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"MEMBER\",\"status\":\"FROZEN\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString())
                .path("data").path("id").asLong();

        assertThat(jobs.processNext()).isTrue();
        byte[] content = mockMvc.perform(get("/api/v1/jobs/" + id + "/result").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String csv = new String(content, StandardCharsets.UTF_8);
        assertThat(csv).contains("会员编号", "陈安然", "*******1003", "已冻结")
                .doesNotContain("13700001003");
    }

    @Test
    void serviceCatalogExportUsesItsDedicatedPermissionAndCurrentStore() throws Exception {
        MockHttpSession session = login();
        mockMvc.perform(post("/api/v1/auth/current-store").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":2}"))
                .andExpect(status().isOk());
        long id = json(mockMvc.perform(post("/api/v1/exports").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"SERVICE_CATALOG\",\"keyword\":\"SVC001\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString())
                .path("data").path("id").asLong();

        assertThat(jobs.processNext()).isTrue();
        String csv = new String(mockMvc.perform(get("/api/v1/jobs/" + id + "/result").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("项目编号", "门店售价", "SVC001");
    }

    @Test
    void productCatalogExportUsesItsDedicatedPermissionAndCurrentStore() throws Exception {
        MockHttpSession session = login();
        mockMvc.perform(post("/api/v1/auth/current-store").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":2}"))
                .andExpect(status().isOk());
        long id = json(mockMvc.perform(post("/api/v1/exports").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"PRODUCT_CATALOG\",\"keyword\":\"PRD001\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString())
                .path("data").path("id").asLong();

        assertThat(jobs.processNext()).isTrue();
        String csv = new String(mockMvc.perform(get("/api/v1/jobs/" + id + "/result").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("产品编号", "门店售价", "库存跟踪", "PRD001", "98.00");
    }

    @Test
    void serviceCatalogImportCreatesValidRowsAndReturnsRowErrors() throws Exception {
        MockHttpSession session = login();
        String csv = "\ufeff\"项目编号\",\"项目名称\",\"分类编号\",\"时长(分钟)\",\"成本\",\"标准售价\",\"门店售价\",\"项目说明\"\r\n"
                + "\"SVC-IMPORT-1\",\"导入服务\",\"NAIL_SERVICE\",\"60\",\"20.00\",\"100.00\",\"88.00\",\"自动化测试\"\r\n"
                + "\"SVC001\",\"冲突项目\",\"NAIL_SERVICE\",\"60\",\"30.00\",\"168.00\",\"168.00\",\"\"\r\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "服务项目.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        JsonNode created = json(mockMvc.perform(multipart("/api/v1/services/import").file(file)
                        .with(csrf()).session(session))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(created.has("inputFileId")).isFalse();
        long id = created.path("id").asLong();

        assertThat(jobs.processNext()).isTrue();
        JsonNode detail = json(mockMvc.perform(get("/api/v1/jobs/" + id).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(detail.path("status").asText()).isEqualTo("PARTIAL");
        assertThat(detail.path("successCount").asInt()).isEqualTo(1);
        assertThat(detail.path("failureCount").asInt()).isEqualTo(1);

        String result = new String(mockMvc.perform(get("/api/v1/jobs/" + id + "/result").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(result).contains("SVC-IMPORT-1", "成功", "SVC001", "失败", "内容不一致");
        String services = mockMvc.perform(get("/api/v1/services?storeId=1&keyword=SVC-IMPORT-1").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(services).contains("SVC-IMPORT-1", "导入服务");
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"TestPassword!2026\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
