package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;

public record ServiceItemUpdate(
        long id,
        String name,
        long categoryId,
        int durationMinutes,
        BigDecimal costAmount,
        BigDecimal listPrice,
        String description,
        String status,
        long storeId,
        BigDecimal storePrice,
        String saleStatus,
        String version,
        long updatedBy) {
}
