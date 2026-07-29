package com.yuezhijian.server.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuezhijian.server.common.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileObjectServiceTest {
    private FileObjectService service;

    @BeforeEach
    void setUp() {
        service = new FileObjectService(
                new MemoryFileObjectRepository(),
                new MemoryObjectStorage(),
                new FileStorageProperties(1024, 2, ".data/test-uploads", null, null, null, "test"));
    }

    @Test
    void uploadDownloadAndSoftDeleteKeepThePrivateObjectBehindBusinessBinding() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};
        BusinessAttachmentItem uploaded = service.upload(
                "SERVICE_FEEDBACK", 12, 2, "SERVICE_FEEDBACK_ATTACHMENT", "EVIDENCE",
                new MockMultipartFile("file", "现场照片.png", "image/png", png), 1);

        assertThat(uploaded.originalName()).isEqualTo("现场照片.png");
        assertThat(uploaded.contentType()).isEqualTo("image/png");
        assertThat(uploaded.sha256()).hasSize(64);
        assertThat(service.attachments("SERVICE_FEEDBACK", 12)).containsExactly(uploaded);
        assertThat(service.download("SERVICE_FEEDBACK", 12, uploaded.id()).content()).isEqualTo(png);
        assertThatThrownBy(() -> service.download("SERVICE_FEEDBACK", 13, uploaded.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        service.remove("SERVICE_FEEDBACK", 12, uploaded.id(), 1);
        assertThat(service.attachments("SERVICE_FEEDBACK", 12)).isEmpty();
        assertThatThrownBy(() -> service.download("SERVICE_FEEDBACK", 12, uploaded.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fileHeaderAndExtensionMustBothMatchTheAllowList() {
        byte[] executable = "MZ-not-an-image".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> service.upload(
                "SERVICE_FEEDBACK", 1, 2, "SERVICE_FEEDBACK_ATTACHMENT", "EVIDENCE",
                new MockMultipartFile("file", "伪装照片.jpg", "image/jpeg", executable), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只允许上传");

        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> service.upload(
                "SERVICE_FEEDBACK", 1, 2, "SERVICE_FEEDBACK_ATTACHMENT", "EVIDENCE",
                new MockMultipartFile("file", "扩展名错误.png", "image/png", pdf), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("扩展名");
    }

    @Test
    void attachmentCountLimitIsCheckedBeforeWritingAnotherObject() {
        byte[] pdf = "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII);
        for (int index = 1; index <= 2; index++) {
            service.upload(
                    "SERVICE_FEEDBACK", 7, 2, "SERVICE_FEEDBACK_ATTACHMENT", "EVIDENCE",
                    new MockMultipartFile("file", "凭证" + index + ".pdf", "application/pdf", pdf), 1);
        }
        assertThatThrownBy(() -> service.upload(
                "SERVICE_FEEDBACK", 7, 2, "SERVICE_FEEDBACK_ATTACHMENT", "EVIDENCE",
                new MockMultipartFile("file", "第三份.pdf", "application/pdf", pdf), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多上传2个");
    }

    @Test
    void generatedCsvUsesTheSamePrivateStorageAndIntegrityCheck() {
        byte[] content = "\ufeff\"标题\"\r\n\"结果\"\r\n".getBytes(StandardCharsets.UTF_8);
        FileObjectItem file = service.storeGenerated(
                "ASYNC_JOB_RESULT", "反馈导出.csv", "text/csv", content, 1);

        assertThat(file.originalName()).isEqualTo("反馈导出.csv");
        assertThat(file.contentType()).isEqualTo("text/csv");
        assertThat(service.downloadGenerated(file.id()).content()).isEqualTo(content);
    }
}
