package com.yuezhijian.server.product;

import java.math.BigDecimal;

public record ProductSummary(
        long id, String code, String name, long categoryId, String categoryName,
        long unitId, String unitName, String barcode, BigDecimal costPrice,
        BigDecimal salePrice, BigDecimal storePrice, boolean trackStock,
        String saleStatus, String status) {
}
