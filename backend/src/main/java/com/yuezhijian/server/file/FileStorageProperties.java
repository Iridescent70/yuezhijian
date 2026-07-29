package com.yuezhijian.server.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-storage")
public record FileStorageProperties(
        long maxUploadBytes,
        int maxAttachmentsPerBusiness,
        String localRoot,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket) {
    public FileStorageProperties {
        if (maxUploadBytes <= 0) maxUploadBytes = 10L * 1024 * 1024;
        if (maxAttachmentsPerBusiness <= 0) maxAttachmentsPerBusiness = 10;
        if (localRoot == null || localRoot.isBlank()) localRoot = ".data/uploads";
        if (endpoint == null || endpoint.isBlank()) endpoint = "http://localhost:9000";
        if (bucket == null || bucket.isBlank()) bucket = "yuezhijian-dev";
    }
}
