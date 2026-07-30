package com.yuezhijian.server.servicearea;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServiceArea(
        long id,
        long storeId,
        String storeCode,
        String storeName,
        String city,
        String district,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        BigDecimal radiusKm,
        BigDecimal visitFee,
        String status,
        LocalDateTime updatedAt,
        Long updatedBy,
        String updatedByName,
        String version) {
}
