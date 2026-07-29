package com.yuezhijian.server.product;

import com.yuezhijian.server.audit.AuditService;
import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreDataScope;
import com.yuezhijian.server.masterdata.CategoryOption;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import com.yuezhijian.server.masterdata.UnitOption;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository repository;
    private final MasterDataRepository masterData;
    private final AccessCatalogService accessCatalog;
    private final StoreDataScope storeDataScope;
    private final AuditService auditService;

    public ProductService(
            ProductRepository repository,
            MasterDataRepository masterData,
            AccessCatalogService accessCatalog,
            StoreDataScope storeDataScope,
            AuditService auditService) {
        this.repository = repository;
        this.masterData = masterData;
        this.accessCatalog = accessCatalog;
        this.storeDataScope = storeDataScope;
        this.auditService = auditService;
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

    @Transactional
    public long create(CreateProductRequest request, String username) {
        requireReferences(request.categoryId(), request.unitId());
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.findByCode(code).isPresent()) throw new DuplicateResourceException("产品编号已存在");
        String barcode = blankToNull(request.barcode());
        requireAvailableBarcode(barcode, null);
        validateAmount(request.costPrice(), "成本");
        validateAmount(request.salePrice(), "标准售价");
        validateAmount(request.storePrice(), "门店售价");
        List<Long> storeIds = new LinkedHashSet<>(request.storeIds()).stream().toList();
        storeIds.forEach(storeDataScope::require);
        long operatorId = operatorId(username);
        long id = repository.create(new NewProduct(
                code, request.name().trim(), request.categoryId(), request.unitId(), barcode,
                request.costPrice(), request.salePrice(), request.storePrice(), request.trackStock(), storeIds,
                blankToNull(request.description()), operatorId));
        ProductDetail created = repository.find(id).orElseThrow();
        auditService.record("CATALOG", "CREATE", "PRODUCT", id, null, null,
                productSnapshot(created, null), operatorId);
        return id;
    }

    @Transactional
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
        requireAvailableBarcode(barcode, id);
        String description = blankToNull(request.description());
        String status = normalize(request.status(), Set.of("ACTIVE", "DISABLED"), "产品状态无效");
        String saleStatus = normalize(request.saleStatus(), Set.of("ON_SALE", "OFF_SALE"), "销售状态无效");
        if (coreChanged(current, request, barcode, description, status)) {
            current.stores().forEach(store -> storeDataScope.require(store.storeId()));
        }
        long operatorId = operatorId(username);
        ProductDetail saved = repository.update(new ProductUpdate(
                id, request.name().trim(), request.categoryId(), request.unitId(), barcode,
                request.costPrice(), request.salePrice(), request.trackStock(), description, status,
                request.storeId(), request.storePrice(), saleStatus, request.version(), operatorId));
        auditService.record("CATALOG", "UPDATE", "PRODUCT", id, request.storeId(),
                productSnapshot(current, request.storeId()), productSnapshot(saved, request.storeId()), operatorId);
        return copyWithStores(saved, saved.stores().stream()
                .filter(store -> storeDataScope.canAccess(store.storeId())).toList());
    }

    @Transactional
    public ProductImportOutcome importProduct(ProductImportRow row, long storeId, long operatorId) {
        String code = required(row.code(), 64, "产品编号").toUpperCase(Locale.ROOT);
        String name = required(row.name(), 200, "产品名称");
        String categoryCode = required(row.categoryCode(), 64, "分类编号").toUpperCase(Locale.ROOT);
        String unitCode = required(row.unitCode(), 64, "单位编号").toUpperCase(Locale.ROOT);
        CategoryOption category = masterData.categories("PRODUCT").stream()
                .filter(item -> item.code().equalsIgnoreCase(categoryCode) && "ACTIVE".equals(item.status()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("分类编号不存在或已停用"));
        UnitOption unit = masterData.units().stream()
                .filter(item -> item.code().equalsIgnoreCase(unitCode) && "ACTIVE".equals(item.status()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("单位编号不存在或已停用"));
        String barcode = blankToNull(row.barcode());
        if (barcode != null && barcode.length() > 64) throw new IllegalArgumentException("条码不能超过64个字符");
        validateAmount(row.costPrice(), "成本");
        validateAmount(row.salePrice(), "标准售价");
        validateAmount(row.storePrice(), "门店售价");
        String description = blankToNull(row.description());
        if (description != null && description.length() > 1000) {
            throw new IllegalArgumentException("产品说明不能超过1000个字符");
        }
        ProductDetail existing = repository.findByCode(code).orElse(null);
        if (existing != null) {
            if (sameImport(existing, name, category.id(), unit.id(), row, barcode, description, storeId)) {
                return new ProductImportOutcome(existing.id(), false, "已存在且内容一致");
            }
            throw new DuplicateResourceException("产品编号已存在且内容不一致");
        }
        requireAvailableBarcode(barcode, null);
        long id = repository.create(new NewProduct(
                code, name, category.id(), unit.id(), barcode, row.costPrice(), row.salePrice(),
                row.storePrice(), row.trackStock(), List.of(storeId), description, operatorId));
        ProductDetail created = repository.find(id).orElseThrow();
        auditService.record("CATALOG", "IMPORT_CREATE", "PRODUCT", id, storeId, null,
                productSnapshot(created, storeId), operatorId);
        return new ProductImportOutcome(id, true, "已新建");
    }

    @Transactional
    public ProductBatchResult batchSaleStatus(
            BatchProductSaleStatusRequest request, long storeId, String username) {
        storeDataScope.require(storeId);
        String saleStatus = normalize(
                request.saleStatus(), Set.of("ON_SALE", "OFF_SALE"), "销售状态无效");
        long operatorId = operatorId(username);
        List<ProductBatchItemResult> results = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(request.productIds())) {
            ProductDetail product = repository.find(id).orElse(null);
            if (product == null) {
                results.add(new ProductBatchItemResult(
                        id, null, null, "FAILED", "产品不存在或当前门店未配置"));
                continue;
            }
            ProductStoreConfig store = product.stores().stream()
                    .filter(item -> item.storeId() == storeId).findFirst().orElse(null);
            if (store == null) {
                results.add(new ProductBatchItemResult(
                        id, null, null, "FAILED", "产品不存在或当前门店未配置"));
                continue;
            }
            if (saleStatus.equals(store.saleStatus())) {
                results.add(batchItem(product, "SKIPPED", "已经是目标销售状态"));
                continue;
            }
            if (repository.updateSaleStatus(id, storeId, saleStatus, operatorId)) {
                ProductDetail saved = repository.find(id).orElseThrow();
                auditService.record("CATALOG", "BATCH_SALE_STATUS", "PRODUCT", id, storeId,
                        productSnapshot(product, storeId), productSnapshot(saved, storeId), operatorId);
                results.add(batchItem(product, "SUCCESS", "ON_SALE".equals(saleStatus) ? "已上架" : "已下架"));
            } else {
                results.add(batchItem(product, "FAILED", "产品门店配置已变化，请刷新后重试"));
            }
        }
        return ProductBatchResult.of("BATCH_SALE_STATUS", results);
    }

    private ProductDetail requireProduct(long id) {
        ProductDetail item = repository.find(id).orElseThrow(() -> new ResourceNotFoundException("产品不存在"));
        storeDataScope.requireAny(item.stores().stream().map(ProductStoreConfig::storeId).toList());
        return item;
    }

    private boolean sameImport(
            ProductDetail existing,
            String name,
            long categoryId,
            long unitId,
            ProductImportRow row,
            String barcode,
            String description,
            long storeId) {
        ProductStoreConfig store = existing.stores().stream()
                .filter(item -> item.storeId() == storeId).findFirst().orElse(null);
        return store != null && existing.name().equals(name) && existing.categoryId() == categoryId
                && existing.unitId() == unitId && Objects.equals(existing.barcode(), barcode)
                && existing.costPrice().compareTo(row.costPrice()) == 0
                && existing.salePrice().compareTo(row.salePrice()) == 0
                && store.storePrice().compareTo(row.storePrice()) == 0
                && existing.trackStock() == row.trackStock()
                && "ACTIVE".equals(existing.status()) && "ON_SALE".equals(store.saleStatus())
                && Objects.equals(existing.description(), description);
    }

    private void requireAvailableBarcode(String barcode, Long currentId) {
        if (barcode == null) return;
        ProductDetail existing = repository.findByBarcode(barcode).orElse(null);
        if (existing != null && (currentId == null || existing.id() != currentId)) {
            throw new DuplicateResourceException("产品条码已存在");
        }
    }

    private ProductBatchItemResult batchItem(ProductDetail product, String status, String message) {
        return new ProductBatchItemResult(product.id(), product.code(), product.name(), status, message);
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

    private Map<String, Object> productSnapshot(ProductDetail item, Long storeId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", item.code());
        snapshot.put("name", item.name());
        snapshot.put("categoryName", item.categoryName());
        snapshot.put("unitName", item.unitName());
        snapshot.put("barcode", item.barcode());
        snapshot.put("costPrice", item.costPrice());
        snapshot.put("salePrice", item.salePrice());
        snapshot.put("trackStock", item.trackStock());
        snapshot.put("description", item.description());
        snapshot.put("status", item.status());
        if (storeId != null) {
            item.stores().stream().filter(store -> store.storeId() == storeId).findFirst().ifPresent(store -> {
                snapshot.put("storeName", store.storeName());
                snapshot.put("storePrice", store.storePrice());
                snapshot.put("saleStatus", store.saleStatus());
            });
        }
        return snapshot;
    }

    private void validateAmount(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.scale() > 4 || value.precision() - value.scale() > 15) {
            throw new IllegalArgumentException(field + "必须是最多15位整数、4位小数的非负金额");
        }
    }

    private String required(String value, int maxLength, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) throw new IllegalArgumentException(field + "不能为空");
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "不能超过" + maxLength + "个字符");
        }
        return normalized;
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
