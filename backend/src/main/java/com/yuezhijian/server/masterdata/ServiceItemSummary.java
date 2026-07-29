package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;

public record ServiceItemSummary(
        long id,
        String code,
        String name,
        long categoryId,
        String categoryName,
        int durationMinutes,
        BigDecimal costAmount,
        BigDecimal listPrice,
        BigDecimal storePrice,
        String saleStatus,
        String status) {
}
