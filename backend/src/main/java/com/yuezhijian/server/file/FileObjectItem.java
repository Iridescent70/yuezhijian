package com.yuezhijian.server.file;

import java.time.LocalDateTime;

public record FileObjectItem(
        long id,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256,
        String purpose,
        LocalDateTime createdAt,
        long ownerUserId) {
}
