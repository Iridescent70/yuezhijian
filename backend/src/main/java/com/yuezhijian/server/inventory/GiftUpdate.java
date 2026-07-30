package com.yuezhijian.server.inventory;

import java.math.BigDecimal;

public record GiftUpdate(
        long id,
        String name,
        long categoryId,
        long unitId,
        int pointPrice,
        BigDecimal costPrice,
        BigDecimal lowStockThreshold,
        String description,
        String status,
        String version,
        long operatorId) {
}
