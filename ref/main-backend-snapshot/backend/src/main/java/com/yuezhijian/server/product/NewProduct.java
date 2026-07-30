package com.yuezhijian.server.product;

import java.math.BigDecimal;
import java.util.List;

public record NewProduct(
        String code, String name, long categoryId, long unitId, String barcode,
        BigDecimal costPrice, BigDecimal salePrice, BigDecimal storePrice,
        boolean trackStock, List<Long> storeIds, String description, long createdBy) {
}
