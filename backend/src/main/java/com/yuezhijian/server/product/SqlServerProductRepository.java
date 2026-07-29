package com.yuezhijian.server.product;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlserver")
public class SqlServerProductRepository implements ProductRepository {
    private final ProductMapper mapper;

    public SqlServerProductRepository(ProductMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ProductSummary> products(Long storeId, Long categoryId, String saleStatus, String keyword) {
        return mapper.findProducts(storeId, categoryId, saleStatus, keyword);
    }

    @Override
    public Optional<ProductDetail> find(long id) {
        return detail(mapper.find(id));
    }

    @Override
    public Optional<ProductDetail> findByCode(String code) {
        return detail(mapper.findByCode(code));
    }

    @Override
    @Transactional
    public long create(NewProduct product) {
        long id = mapper.insert(product);
        product.storeIds().forEach(storeId -> mapper.insertStore(id, storeId, product.storePrice(), product.createdBy()));
        return id;
    }

    @Override
    @Transactional
    public ProductDetail update(ProductUpdate update) {
        if (mapper.update(update) == 0) throw new DuplicateResourceException("产品已被他人修改，请刷新后重试");
        if (mapper.updateStore(update) == 0) throw new IllegalArgumentException("产品未配置到所选门店");
        return find(update.id()).orElseThrow();
    }

    private Optional<ProductDetail> detail(ProductRow row) {
        if (row == null) return Optional.empty();
        return Optional.of(new ProductDetail(
                row.id(), row.code(), row.name(), row.categoryId(), row.categoryName(), row.unitId(),
                row.unitName(), row.barcode(), row.costPrice(), row.salePrice(), row.trackStock(),
                row.description(), row.status(), mapper.findStores(row.id()), row.version()));
    }
}
