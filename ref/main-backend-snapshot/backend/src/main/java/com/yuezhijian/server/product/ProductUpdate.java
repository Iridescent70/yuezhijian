package com.yuezhijian.server.product;

import java.math.BigDecimal;

public record ProductUpdate(
        long id, String name, long categoryId, long unitId, String barcode,
        BigDecimal costPrice, BigDecimal salePrice, boolean trackStock, String description,
        String status, long storeId, BigDecimal storePrice, String saleStatus,
        String version, long updatedBy) {
}
