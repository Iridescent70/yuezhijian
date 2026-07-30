package com.yuezhijian.server.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CountSummary(
        long id,
        String countNo,
        String name,
        long storeId,
        String storeName,
        LocalDate countDate,
        int lineCount,
        int differenceLineCount,
        BigDecimal differenceQuantity,
        String status,
        LocalDateTime createdAt,
        String createdByName,
        String version) {
}
