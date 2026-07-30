package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;

public record PositionOption(
        long id,
        String code,
        String name,
        int level,
        BigDecimal defaultServiceRate,
        BigDecimal defaultSalesRate,
        String status,
        String version) {
}
