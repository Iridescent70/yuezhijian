package com.yuezhijian.server.product;

import java.math.BigDecimal;

public record ProductStoreConfig(long storeId, String storeName, BigDecimal storePrice, String saleStatus) {
}
