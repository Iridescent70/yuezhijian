package com.yuezhijian.server.servicearea;

import java.math.BigDecimal;

public record ServiceAreaUpdate(
        long id,
        String city,
        String district,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        BigDecimal radiusKm,
        BigDecimal visitFee,
        String status,
        String version,
        long operatorId) {
}
