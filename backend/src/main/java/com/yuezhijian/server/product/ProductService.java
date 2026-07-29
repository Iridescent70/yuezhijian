package com.yuezhijian.server.product;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreDataScope;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProductService {
    private final ProductRepository repository;
    private final MasterDataRepository masterData;
    private final AccessCatalogService accessCatalog;
    private final StoreDataScope storeDataScope;

    public ProductService(
            ProductRepository repository,
            MasterDataRepository masterData,
            AccessCatalogService accessCatalog,
            StoreDataScope storeDataScope) {
        this.repository = repository;
        this.masterData = masterData;
        this.accessCatalog = accessCatalog;
        this.storeDataScope = storeDataScope;
    }

    public List<ProductSummary> products(Long storeId, Long categoryId, String saleStatus, String keyword) {
        String normalizedSaleStatus = optionalStatus(saleStatus);
        return repository.products(
                storeDataScope.constrainNullable(storeId), categoryId, normalizedSaleStatus, blankToNull(keyword));
    }

    public ProductDetail product(long id) {
        ProductDetail item = requireProduct(id);
        return copyWithStores(item, item.stores().stream()
                .filter(store -> storeDataScope.canAccess(store.storeId())).toList());
    }

    public long create(CreateProductRequest request, String username) {
        requireReferences(request.categoryId(), request.unitId());
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.findByCode(code).isPresent()) throw new DuplicateResourceException("产品编号已存在");
        validateAmount(request.costPrice(), "成本");
        validateAmount(request.salePrice(), "标准售价");
        validateAmount(request.storePrice(), "门店售价");
        List<Long> storeIds = new LinkedHashSet<>(request.storeIds()).stream().toList();
        storeIds.forEach(storeDataScope::require);
        return repository.create(new NewProduct(
                code, request.name().trim(), request.categoryId(), request.unitId(), blankToNull(request.barcode()),
                request.costPrice(), request.salePrice(), request.storePrice(), request.trackStock(), storeIds,
                blankToNull(request.description()), operatorId(username)));
    }

    public ProductDetail update(long id, UpdateProductRequest request, String username) {
        ProductDetail current = requireProduct(id);
        storeDataScope.require(request.storeId());
        if (current.stores().stream().noneMatch(store -> store.storeId() == request.storeId())) {
            throw new IllegalArgumentException("产品未配置到所选门店");
        }
        requireReferences(request.categoryId(), request.unitId());
        validateAmount(request.costPrice(), "成本");
        validateAmount(request.salePrice(), "标准售价");
        validateAmount(request.storePrice(), "门店售价");
        String barcode = blankToNull(request.barcode());
        String description = blankToNull(request.description());
        String status = normalize(request.status(), Set.of("ACTIVE", "DISABLED"), "产品状态无效");
        String saleStatus = normalize(request.saleStatus(), Set.of("ON_SALE", "OFF_SALE"), "销售状态无效");
        if (coreChanged(current, request, barcode, description, status)) {
            current.stores().forEach(store -> storeDataScope.require(store.storeId()));
        }
        ProductDetail saved = repository.update(new ProductUpdate(
                id, request.name().trim(), request.categoryId(), request.unitId(), barcode,
                request.costPrice(), request.salePrice(), request.trackStock(), description, status,
                request.storeId(), request.storePrice(), saleStatus, request.version(), operatorId(username)));
        return copyWithStores(saved, saved.stores().stream()
                .filter(store -> storeDataScope.canAccess(store.storeId())).toList());
    }

    private ProductDetail requireProduct(long id) {
        ProductDetail item = repository.find(id).orElseThrow(() -> new ResourceNotFoundException("产品不存在"));
        storeDataScope.requireAny(item.stores().stream().map(ProductStoreConfig::storeId).toList());
        return item;
    }

    private void requireReferences(long categoryId, long unitId) {
        if (masterData.categories("PRODUCT").stream()
                .noneMatch(item -> item.id() == categoryId && "ACTIVE".equals(item.status()))) {
            throw new IllegalArgumentException("产品分类不存在或已停用");
        }
        if (masterData.units().stream().noneMatch(item -> item.id() == unitId && "ACTIVE".equals(item.status()))) {
            throw new IllegalArgumentException("物料单位不存在或已停用");
        }
    }

    private boolean coreChanged(
            ProductDetail current, UpdateProductRequest request, String barcode, String description, String status) {
        return !current.name().equals(request.name().trim()) || current.categoryId() != request.categoryId()
                || current.unitId() != request.unitId() || !Objects.equals(current.barcode(), barcode)
                || current.costPrice().compareTo(request.costPrice()) != 0
                || current.salePrice().compareTo(request.salePrice()) != 0
                || current.trackStock() != request.trackStock()
                || !Objects.equals(current.description(), description) || !current.status().equals(status);
    }

    private ProductDetail copyWithStores(ProductDetail item, List<ProductStoreConfig> stores) {
        return new ProductDetail(
                item.id(), item.code(), item.name(), item.categoryId(), item.categoryName(), item.unitId(),
                item.unitName(), item.barcode(), item.costPrice(), item.salePrice(), item.trackStock(),
                item.description(), item.status(), stores, item.version());
    }

    private void validateAmount(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.scale() > 4 || value.precision() - value.scale() > 15) {
            throw new IllegalArgumentException(field + "必须是最多15位整数、4位小数的非负金额");
        }
    }

    private long operatorId(String username) {
        return accessCatalog.userIdentity(username).id();
    }

    private String optionalStatus(String value) {
        return value == null || value.isBlank() ? null
                : normalize(value, Set.of("ON_SALE", "OFF_SALE"), "销售状态无效");
    }

    private String normalize(String value, Set<String> values, String message) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!values.contains(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
