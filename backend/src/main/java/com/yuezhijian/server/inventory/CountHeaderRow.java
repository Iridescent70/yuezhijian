package com.yuezhijian.server.inventory;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CountHeaderRow(
        long id,
        String countNo,
        String name,
        long storeId,
        String storeName,
        LocalDate countDate,
        String remarks,
        String status,
        LocalDateTime confirmedAt,
        LocalDateTime voidedAt,
        String actionReason,
        LocalDateTime createdAt,
        long createdBy,
        String createdByName,
        String idempotencyKey,
        String version) {
}
