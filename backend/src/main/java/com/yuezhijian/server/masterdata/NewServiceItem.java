package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;
import java.util.List;

public record NewServiceItem(
        String code,
        String name,
        long categoryId,
        int durationMinutes,
        BigDecimal costAmount,
        BigDecimal listPrice,
        BigDecimal storePrice,
        List<Long> storeIds,
        String description,
        long createdBy) {
}
