package com.yuezhijian.server.product;

import java.math.BigDecimal;

public record ProductImportRow(
        String code,
        String name,
        String categoryCode,
        String unitCode,
        String barcode,
        BigDecimal costPrice,
        BigDecimal salePrice,
        BigDecimal storePrice,
        boolean trackStock,
        String description) {
}
