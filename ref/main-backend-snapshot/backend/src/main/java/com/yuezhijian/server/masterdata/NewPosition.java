package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;

public record NewPosition(
        String code,
        String name,
        int level,
        BigDecimal defaultServiceRate,
        BigDecimal defaultSalesRate,
        long createdBy) {
}
