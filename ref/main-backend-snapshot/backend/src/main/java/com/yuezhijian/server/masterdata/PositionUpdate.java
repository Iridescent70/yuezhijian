package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;

public record PositionUpdate(
        long id,
        String name,
        int level,
        BigDecimal defaultServiceRate,
        BigDecimal defaultSalesRate,
        String status,
        String version,
        long updatedBy) {
}
