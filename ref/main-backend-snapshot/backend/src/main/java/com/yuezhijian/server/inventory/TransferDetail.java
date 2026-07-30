package com.yuezhijian.server.inventory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TransferDetail(
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
        String createdByName,
        String version,
        List<TransferLine> lines) {
}
