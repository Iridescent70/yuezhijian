package com.yuezhijian.server.audit;

import java.time.LocalDateTime;

public record AuditLogRow(
        long id,
        String traceId,
        Long userId,
        String operatorName,
        Long storeId,
        String module,
        String action,
        String objectType,
        String objectId,
        String beforeJson,
        String afterJson,
        String result,
        String errorCode,
        String ip,
        LocalDateTime occurredAt) {
}
