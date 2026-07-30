package com.yuezhijian.server;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
        "app.bootstrap.password=TestPassword!2026"
})
@AutoConfigureMockMvc
class BannerFlowTest {
    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
    };
    private static final byte[] JPEG = new byte[] {
            (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void bannerCanBeUploadedDisplayedReplacedAndDisabled() throws Exception {
        MockHttpSession session = login();
        JsonNode created = create(session);
        long id = created.path("id").asLong();
        String initialVersion = created.path("version").asText();

        mockMvc.perform(get("/api/v1/banners/active").session(session).param("positionCode", "PC_HOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].title".formatted(id))
                        .value(org.hamcrest.Matchers.hasItem("自动化首页图")));
        mockMvc.perform(get("/api/v1/banners/active/{id}/image", id).session(session))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));

        JsonNode updated = data(mockMvc.perform(put("/api/v1/banners/{id}", id)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "positionCode":"PC_HOME","title":"自动化首页图已编辑",
                                  "linkType":"INTERNAL","linkValue":"/app/members","sortNo":5,
                                  "status":"ACTIVE","version":"%s"
                                }
                                """.formatted(initialVersion)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(put("/api/v1/banners/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "positionCode":"PC_HOME","title":"不安全链接",
                                  "linkType":"EXTERNAL","linkValue":"http://example.com","sortNo":5,
                                  "status":"ACTIVE","version":"%s"
                                }
                                """.formatted(updated.path("version").asText())))
                .andExpect(status().isBadRequest());

        MockMultipartFile replacement = new MockMultipartFile(
                "file", "新首页图.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG);
        String replacedBody = mockMvc.perform(multipart("/api/v1/banners/{id}/image", id)
                        .file(replacement).param("version", updated.path("version").asText())
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(csrf()).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode replaced = data(replacedBody);
        mockMvc.perform(get("/api/v1/banners/{id}/image", id).session(session))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG));

        JsonNode disabled = data(mockMvc.perform(put("/api/v1/banners/{id}", id)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "positionCode":"PC_HOME","title":"自动化首页图已编辑",
                                  "linkType":"INTERNAL","linkValue":"/app/members","sortNo":5,
                                  "status":"DISABLED","version":"%s"
                                }
                                """.formatted(replaced.path("version").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(disabled.path("status").asText()).isEqualTo("DISABLED");
        mockMvc.perform(get("/api/v1/banners/active/{id}/image", id).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/banners/{id}/image", id).session(session))
                .andExpect(status().isOk()).andExpect(content().bytes(JPEG));

        mockMvc.perform(put("/api/v1/banners/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "positionCode":"PC_HOME","title":"过期修改",
                                  "linkType":"NONE","sortNo":5,"status":"ACTIVE","version":"%s"
                                }
                                """.formatted(initialVersion)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/audit-logs").session(session)
                        .param("objectType", "BANNER").param("objectId", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(4)));
    }

    @Test
    void bannerUploadRejectsNonImageContent() throws Exception {
        MockHttpSession session = login();
        MockMultipartFile request = jsonPart("""
                {"positionCode":"PC_HOME","title":"伪装文件","linkType":"NONE","sortNo":10}
                """);
        MockMultipartFile file = new MockMultipartFile(
                "file", "伪装图片.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        mockMvc.perform(multipart("/api/v1/banners").file(request).file(file).with(csrf()).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只允许上传JPG、PNG或WEBP图片"));
    }

    @Test
    void futureBannerIsKeptOutOfTheActiveFeed() throws Exception {
        MockHttpSession session = login();
        String from = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS).toString();
        String to = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.SECONDS).toString();
        MockMultipartFile request = jsonPart("""
                {
                  "positionCode":"PC_HOME","title":"未来首页图","linkType":"NONE","sortNo":20,
                  "validFrom":"%s","validTo":"%s"
                }
                """.formatted(from, to));
        MockMultipartFile file = new MockMultipartFile("file", "未来.png", MediaType.IMAGE_PNG_VALUE, PNG);
        JsonNode future = data(mockMvc.perform(multipart("/api/v1/banners")
                        .file(request).file(file).with(csrf()).session(session))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/banners/active").session(session).param("positionCode", "PC_HOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(future.path("id").asLong())).isEmpty());
        mockMvc.perform(get("/api/v1/banners/active/{id}/image", future.path("id").asLong()).session(session))
                .andExpect(status().isNotFound());
    }

    private JsonNode create(MockHttpSession session) throws Exception {
        MockMultipartFile request = jsonPart("""
                {"positionCode":"PC_HOME","title":"自动化首页图","linkType":"NONE","sortNo":10}
                """);
        MockMultipartFile file = new MockMultipartFile("file", "首页图.png", MediaType.IMAGE_PNG_VALUE, PNG);
        String body = mockMvc.perform(multipart("/api/v1/banners")
                        .file(request).file(file).with(csrf()).session(session))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return data(body);
    }

    private MockMultipartFile jsonPart(String value) {
        return new MockMultipartFile(
                "request", "request.json", MediaType.APPLICATION_JSON_VALUE,
                value.getBytes(StandardCharsets.UTF_8));
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
