package com.yuezhijian.server.inventory;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransferHeaderRow(
        long id,
        String transferNo,
        long sourceStoreId,
        String sourceStoreName,
        long targetStoreId,
        String targetStoreName,
        LocalDate transferDate,
        String remarks,
        String status,
        LocalDateTime confirmedAt,
        LocalDateTime voidedAt,
        LocalDateTime reversedAt,
        String actionReason,
        LocalDateTime createdAt,
        long createdBy,
        String createdByName,
        String idempotencyKey,
        String version) {
}
