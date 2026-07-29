package com.yuezhijian.server.file;

import java.time.LocalDateTime;

public record BusinessAttachmentItem(
        long id,
        long fileId,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256,
        String purpose,
        String category,
        LocalDateTime createdAt,
        long createdBy,
        String createdByName) {
}
