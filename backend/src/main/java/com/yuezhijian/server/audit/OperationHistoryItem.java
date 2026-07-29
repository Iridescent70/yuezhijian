package com.yuezhijian.server.audit;

import java.time.LocalDateTime;
import java.util.List;

public record OperationHistoryItem(
        long id,
        String action,
        String actionLabel,
        long operatorId,
        String operatorName,
        Long storeId,
        LocalDateTime occurredAt,
        String traceId,
        List<OperationChange> changes) {
    public OperationHistoryItem {
        changes = List.copyOf(changes);
    }
}
