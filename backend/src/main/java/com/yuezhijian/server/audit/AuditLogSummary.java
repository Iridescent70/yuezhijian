package com.yuezhijian.server.audit;

import java.time.LocalDateTime;

public record AuditLogSummary(
        long id,
        String traceId,
        Long userId,
        String operatorName,
        Long storeId,
        String module,
        String action,
        String objectType,
        String objectId,
        String result,
        String errorCode,
        LocalDateTime occurredAt) {
}
