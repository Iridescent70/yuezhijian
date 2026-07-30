package com.yuezhijian.server.trade;

import java.time.LocalDateTime;

public record BillHistoryItem(
        long id,
        String fromStatus,
        String toStatus,
        String reasonCode,
        String note,
        LocalDateTime occurredAt,
        long operatorId) {
}
