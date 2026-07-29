package com.yuezhijian.server.audit;

import java.time.LocalDateTime;

public record AuditLogQuery(
        Long userId,
        String operator,
        String module,
        String action,
        String objectType,
        String objectId,
        String result,
        LocalDateTime occurredFrom,
        LocalDateTime occurredTo,
        int page,
        int size) {
    public int offset() {
        return (page - 1) * size;
    }
}
