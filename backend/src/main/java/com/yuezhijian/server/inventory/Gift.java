package com.yuezhijian.server.inventory;

import java.math.BigDecimal;

public record Gift(
        long id,
        String code,
        String name,
        long categoryId,
        String categoryName,
        long unitId,
        String unitName,
        int unitDecimalPlaces,
        int pointPrice,
        BigDecimal costPrice,
        BigDecimal lowStockThreshold,
        String description,
        String status,
        String version) {
}
