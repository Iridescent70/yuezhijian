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
class ColorStyleFlowTest {
    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
    };
    private static final byte[] JPEG = new byte[] {
            (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void categoriesStylesAndPrivateAssetsCanBeManagedWithConcurrencyProtection() throws Exception {
        MockHttpSession session = login();
        JsonNode brand = createCategory(session, "BRAND_AUTO", "自动化品牌", null);
        JsonNode tone = createCategory(session, "TONE_AUTO", "自动化色系", brand.path("id").asLong());

        mockMvc.perform(put("/api/v1/color-style-categories/{id}", brand.path("id").asLong())
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":%d,"name":"循环分类","sortNo":10,
                                 "status":"ACTIVE","version":"%s"}
                                """.formatted(tone.path("id").asLong(), brand.path("version").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("分类不能移动到自己的子分类下"));

        MockMultipartFile categoryImage = new MockMultipartFile(
                "file", "品牌.png", MediaType.IMAGE_PNG_VALUE, PNG);
        JsonNode brandWithImage = data(mockMvc.perform(multipart(
                                "/api/v1/color-style-categories/{id}/image", brand.path("id").asLong())
                        .file(categoryImage).param("version", brand.path("version").asText())
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(csrf()).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(get("/api/v1/color-style-categories/{id}/image", brand.path("id").asLong())
                        .session(session))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));

        JsonNode style = data(mockMvc.perform(post("/api/v1/color-styles")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CS-AUTO-01","name":"樱桃红","description":"自动化样例",
                                 "sortNo":5,"categoryIds":[%d,%d,%d]}
                                """.formatted(
                                        brand.path("id").asLong(), tone.path("id").asLong(),
                                        tone.path("id").asLong())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.categoryIds.length()").value(2))
                .andReturn().getResponse().getContentAsString());

        MockMultipartFile material = new MockMultipartFile(
                "file", "樱桃红.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG);
        JsonNode asset = data(mockMvc.perform(multipart(
                                "/api/v1/color-styles/{id}/assets", style.path("id").asLong())
                        .file(material).param("sortNo", "8").with(csrf()).session(session))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(get("/api/v1/color-styles/{styleId}/assets/{assetId}/content",
                        style.path("id").asLong(), asset.path("id").asLong()).session(session))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG));

        JsonNode disabledAsset = data(mockMvc.perform(put(
                                "/api/v1/color-styles/{styleId}/assets/{assetId}",
                                style.path("id").asLong(), asset.path("id").asLong())
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sortNo":9,"status":"DISABLED","version":"%s"}
                                """.formatted(asset.path("version").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(disabledAsset.path("status").asText())
                .isEqualTo("DISABLED");
        mockMvc.perform(put("/api/v1/color-styles/{styleId}/assets/{assetId}",
                        style.path("id").asLong(), asset.path("id").asLong())
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sortNo":10,"status":"ACTIVE","version":"%s"}
                                """.formatted(asset.path("version").asText())))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/color-style-categories/{id}", brand.path("id").asLong())
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"自动化品牌","sortNo":10,"status":"DISABLED","version":"%s"}
                                """.formatted(brandWithImage.path("version").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先停用该分类下的启用子分类"));
        mockMvc.perform(put("/api/v1/color-style-categories/{id}", tone.path("id").asLong())
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":%d,"name":"自动化色系","sortNo":10,
                                 "status":"DISABLED","version":"%s"}
                                """.formatted(brand.path("id").asLong(), tone.path("version").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先停用或调整该分类下的启用色号"));

        mockMvc.perform(get("/api/v1/color-styles").session(session)
                        .param("categoryId", String.valueOf(tone.path("id").asLong()))
                        .param("keyword", "CS-AUTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("樱桃红"))
                .andExpect(jsonPath("$.data.items[0].assets[0].status").value("DISABLED"));
        mockMvc.perform(get("/api/v1/audit-logs").session(session)
                        .param("objectType", "COLOR_STYLE")
                        .param("objectId", String.valueOf(style.path("id").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void duplicateCodesAndDisguisedAssetsAreRejected() throws Exception {
        MockHttpSession session = login();
        JsonNode category = createCategory(session, "UNIQUE_AUTO", "唯一分类", null);
        mockMvc.perform(post("/api/v1/color-style-categories")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"unique_auto\",\"name\":\"重复分类\",\"sortNo\":20}"))
                .andExpect(status().isConflict());

        JsonNode style = data(mockMvc.perform(post("/api/v1/color-styles")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"UNIQUE-COLOR","name":"唯一色号","sortNo":10,"categoryIds":[%d]}
                                """.formatted(category.path("id").asLong())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        MockMultipartFile fake = new MockMultipartFile(
                "file", "伪装.png", MediaType.IMAGE_PNG_VALUE, "%PDF-1.7".getBytes());
        mockMvc.perform(multipart("/api/v1/color-styles/{id}/assets", style.path("id").asLong())
                        .file(fake).with(csrf()).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只允许上传JPG、PNG或WEBP图片"));
    }

    private JsonNode createCategory(
            MockHttpSession session, String code, String name, Long parentId) throws Exception {
        String parent = parentId == null ? "" : "\"parentId\":" + parentId + ",";
        String body = mockMvc.perform(post("/api/v1/color-style-categories")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{%s\"code\":\"%s\",\"name\":\"%s\",\"sortNo\":10}"
                                .formatted(parent, code, name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return data(body);
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
