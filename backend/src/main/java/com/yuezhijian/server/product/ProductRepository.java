package com.yuezhijian.server.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    List<ProductSummary> products(Long storeId, Long categoryId, String saleStatus, String keyword);

    Optional<ProductDetail> find(long id);

    Optional<ProductDetail> findByCode(String code);

    long create(NewProduct product);

    ProductDetail update(ProductUpdate update);
}
