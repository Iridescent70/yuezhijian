package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;

public record ServiceStoreConfig(
        long storeId,
        String storeName,
        BigDecimal storePrice,
        String saleStatus) {
}
