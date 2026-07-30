package com.yuezhijian.server.file;

public record StoredFileObject(
        long attachmentId,
        long fileId,
        String objectKey,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256,
        String purpose,
        String category) {
}
