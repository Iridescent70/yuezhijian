package com.yuezhijian.server.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransferSummary(
        long id,
        String transferNo,
        long sourceStoreId,
        String sourceStoreName,
        long targetStoreId,
        String targetStoreName,
        LocalDate transferDate,
        int lineCount,
        BigDecimal totalQuantity,
        String status,
        LocalDateTime createdAt,
        String createdByName,
        String version) {
}
