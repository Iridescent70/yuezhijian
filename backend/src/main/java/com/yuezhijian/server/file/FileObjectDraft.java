package com.yuezhijian.server.file;

public record FileObjectDraft(
        String objectKey,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256,
        String purpose,
        long ownerUserId) {
}
