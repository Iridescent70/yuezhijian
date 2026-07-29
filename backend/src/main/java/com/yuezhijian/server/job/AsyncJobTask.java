package com.yuezhijian.server.job;

public record AsyncJobTask(
        long id,
        String jobNo,
        String jobType,
        String requestJson,
        long storeId,
        long createdBy) {
}
