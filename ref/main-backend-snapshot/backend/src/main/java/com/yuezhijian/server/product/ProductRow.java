package com.yuezhijian.server.product;

import java.math.BigDecimal;

public record ProductRow(
        long id, String code, String name, long categoryId, String categoryName,
        long unitId, String unitName, String barcode, BigDecimal costPrice,
        BigDecimal salePrice, boolean trackStock, String description, String status, String version) {
}
