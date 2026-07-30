package com.yuezhijian.server.inventory;

import java.math.BigDecimal;

public record NewGift(
        String code,
        String name,
        long categoryId,
        long unitId,
        int pointPrice,
        BigDecimal costPrice,
        BigDecimal lowStockThreshold,
        String description,
        long operatorId) {
}
