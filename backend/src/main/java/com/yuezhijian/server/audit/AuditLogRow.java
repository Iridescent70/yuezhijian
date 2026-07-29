package com.yuezhijian.server.audit;

import java.time.LocalDateTime;

public record AuditLogRow(
        long id,
        String traceId,
        long userId,
        String operatorName,
        Long storeId,
        String module,
        String action,
        String objectType,
        String objectId,
        String beforeJson,
        String afterJson,
        LocalDateTime occurredAt) {
}
