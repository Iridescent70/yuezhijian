package com.yuezhijian.server.inventory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CountDetail(
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
        String createdByName,
        String version,
        List<CountLine> lines) {
}
