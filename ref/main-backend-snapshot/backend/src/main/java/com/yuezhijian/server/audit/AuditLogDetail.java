package com.yuezhijian.server.audit;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AuditLogDetail(
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
        String ip,
        LocalDateTime occurredAt,
        Map<String, String> beforeValues,
        Map<String, String> afterValues) {
    public AuditLogDetail {
        beforeValues = Collections.unmodifiableMap(new LinkedHashMap<>(beforeValues));
        afterValues = Collections.unmodifiableMap(new LinkedHashMap<>(afterValues));
    }
}
