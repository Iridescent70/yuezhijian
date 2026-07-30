package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;
import java.util.List;

public record ServiceItemDetail(
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
        List<ServiceStoreConfig> stores,
        String version) {
    public ServiceItemDetail {
        stores = List.copyOf(stores);
    }
}
