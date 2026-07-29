package com.yuezhijian.server.job;

import java.time.LocalDateTime;

public record AsyncJobItem(
        long id,
        String jobNo,
        String jobName,
        String jobType,
        String status,
        int progress,
        int successCount,
        int failureCount,
        Long resultFileId,
        String resultFileName,
        Long errorFileId,
        String errorFileName,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        long createdBy,
        String createdByName) {
}
