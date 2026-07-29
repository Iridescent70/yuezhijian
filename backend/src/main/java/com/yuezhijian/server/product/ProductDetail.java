package com.yuezhijian.server.product;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetail(
        long id, String code, String name, long categoryId, String categoryName,
        long unitId, String unitName, String barcode, BigDecimal costPrice,
        BigDecimal salePrice, boolean trackStock, String description, String status,
        List<ProductStoreConfig> stores, String version) {
    public ProductDetail {
        stores = List.copyOf(stores);
    }
}
