package com.yuezhijian.server.audit;

public record NewAuditEvent(
        String traceId,
        long userId,
        Long storeId,
        String module,
        String action,
        String objectType,
        String objectId,
        String beforeJson,
        String afterJson) {
}
