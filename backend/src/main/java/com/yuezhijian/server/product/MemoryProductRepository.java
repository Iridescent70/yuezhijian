package com.yuezhijian.server.product;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryProductRepository implements ProductRepository {
    private final Map<Long, ProductDetail> products = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong(401);

    public MemoryProductRepository() {
        products.put(401L, new ProductDetail(
                401, "PRD001", "护甲精华油", 2, "零售产品", 3, "瓶", "690000000001",
                new BigDecimal("35.00"), new BigDecimal("98.00"), true, "门店零售产品", "ACTIVE",
                List.of(new ProductStoreConfig(2, "悦指间示范店", new BigDecimal("98.00"), "ON_SALE")), "1"));
    }

    @Override
    public synchronized List<ProductSummary> products(
            Long storeId, Long categoryId, String saleStatus, String keyword) {
        String normalized = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return products.values().stream()
                .filter(item -> categoryId == null || item.categoryId() == categoryId)
                .filter(item -> normalized == null || item.code().toLowerCase(Locale.ROOT).contains(normalized)
                        || item.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || item.barcode() != null && item.barcode().toLowerCase(Locale.ROOT).contains(normalized))
                .map(item -> summary(item, storeId))
                .filter(java.util.Objects::nonNull)
                .filter(item -> saleStatus == null || item.saleStatus().equals(saleStatus))
                .sorted(Comparator.comparingLong(ProductSummary::id).reversed()).toList();
    }

    @Override
    public synchronized Optional<ProductDetail> find(long id) {
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public synchronized Optional<ProductDetail> findByCode(String code) {
        return products.values().stream().filter(item -> item.code().equals(code)).findFirst();
    }

    @Override
    public synchronized long create(NewProduct product) {
        if (findByCode(product.code()).isPresent()) throw new DuplicateResourceException("产品编号已存在");
        long id = ids.incrementAndGet();
        ProductDetail detail = new ProductDetail(
                id, product.code(), product.name(), product.categoryId(), "零售产品", product.unitId(),
                unitName(product.unitId()), product.barcode(), product.costPrice(), product.salePrice(),
                product.trackStock(), product.description(), "ACTIVE",
                product.storeIds().stream().map(storeId -> new ProductStoreConfig(
                        storeId, storeName(storeId), product.storePrice(), "ON_SALE")).toList(), "1");
        products.put(id, detail);
        return id;
    }

    @Override
    public synchronized ProductDetail update(ProductUpdate update) {
        ProductDetail current = products.get(update.id());
        if (current == null || !current.version().equals(update.version())) {
            throw new DuplicateResourceException("产品已被他人修改，请刷新后重试");
        }
        if (current.stores().stream().noneMatch(store -> store.storeId() == update.storeId())) {
            throw new IllegalArgumentException("产品未配置到所选门店");
        }
        List<ProductStoreConfig> stores = current.stores().stream().map(store -> store.storeId() == update.storeId()
                ? new ProductStoreConfig(store.storeId(), store.storeName(), update.storePrice(), update.saleStatus())
                : store).toList();
        ProductDetail saved = new ProductDetail(
                current.id(), current.code(), update.name(), update.categoryId(), "零售产品", update.unitId(),
                unitName(update.unitId()), update.barcode(), update.costPrice(), update.salePrice(),
                update.trackStock(), update.description(), update.status(), stores,
                String.valueOf(Long.parseLong(current.version()) + 1));
        products.put(saved.id(), saved);
        return saved;
    }

    private ProductSummary summary(ProductDetail item, Long storeId) {
        ProductStoreConfig store = item.stores().stream()
                .filter(value -> storeId == null || value.storeId() == storeId).findFirst().orElse(null);
        return store == null ? null : new ProductSummary(
                item.id(), item.code(), item.name(), item.categoryId(), item.categoryName(), item.unitId(),
                item.unitName(), item.barcode(), item.costPrice(), item.salePrice(), store.storePrice(),
                item.trackStock(), store.saleStatus(), item.status());
    }

    private String unitName(long id) {
        return id == 1 ? "次" : id == 2 ? "件" : "瓶";
    }

    private String storeName(long id) {
        return id == 1 ? "悦指间总部" : "悦指间示范店";
    }
}
