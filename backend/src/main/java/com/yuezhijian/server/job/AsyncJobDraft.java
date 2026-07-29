package com.yuezhijian.server.job;

import java.time.LocalDateTime;

public record AsyncJobDraft(
        String jobNo,
        String jobName,
        String jobType,
        String requestJson,
        long storeId,
        LocalDateTime expiresAt,
        long operatorId) {
}
