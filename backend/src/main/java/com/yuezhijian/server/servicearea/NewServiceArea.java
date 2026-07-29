package com.yuezhijian.server.servicearea;

import java.math.BigDecimal;

public record NewServiceArea(
        long storeId,
        String city,
        String district,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        BigDecimal radiusKm,
        BigDecimal visitFee,
        long operatorId) {
}
