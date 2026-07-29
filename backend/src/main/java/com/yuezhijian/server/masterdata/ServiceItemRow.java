package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;

public record ServiceItemRow(
        long id,
        String code,
        String name,
        long categoryId,
        String categoryName,
        int durationMinutes,
        BigDecimal costAmount,
        BigDecimal listPrice,
        String description,
        String status,
        String version) {
}
