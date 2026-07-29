package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;

public record ServiceImportRow(
        String code,
        String name,
        String categoryCode,
        int durationMinutes,
        BigDecimal costAmount,
        BigDecimal listPrice,
        BigDecimal storePrice,
        String description) {
}
