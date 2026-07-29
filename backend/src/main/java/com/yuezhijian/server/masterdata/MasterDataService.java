package com.yuezhijian.server.masterdata;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreDataScope;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MasterDataService {
    private final MasterDataRepository repository;
    private final AccessCatalogService accessCatalog;
    private final StoreDataScope storeDataScope;

    public MasterDataService(
            MasterDataRepository repository,
            AccessCatalogService accessCatalog,
            StoreDataScope storeDataScope) {
        this.repository = repository;
        this.accessCatalog = accessCatalog;
        this.storeDataScope = storeDataScope;
    }

    public List<PositionOption> positions() {
        return repository.positions();
    }

    public List<CategoryOption> serviceCategories() {
        return repository.serviceCategories();
    }

    public List<EmployeeSummary> employees(Long storeId, String keyword) {
        return repository.employees(storeDataScope.constrainNullable(storeId), blankToNull(keyword));
    }

    public List<WorkstationSummary> workstations(Long storeId) {
        return repository.workstations(storeDataScope.constrainNullable(storeId));
    }

    public List<ServiceItemSummary> services(Long storeId, String keyword) {
        return repository.services(storeDataScope.constrainNullable(storeId), blankToNull(keyword));
    }

    public ServiceItemDetail service(long id) {
        ServiceItemDetail service = requireService(id);
        return copyWithStores(service, service.stores().stream()
                .filter(store -> storeDataScope.canAccess(store.storeId())).toList());
    }

    public CreatedResource createEmployee(CreateEmployeeRequest request, String username) {
        storeDataScope.require(request.primaryStoreId());
        boolean positionExists = repository.positions().stream()
                .anyMatch(position -> position.id() == request.positionId() && "ACTIVE".equals(position.status()));
        if (!positionExists) {
            throw new IllegalArgumentException("所选职务不存在或已停用");
        }
        return repository.createEmployee(new NewEmployee(
                request.employeeNo().trim().toUpperCase(Locale.ROOT),
                request.name().trim(),
                normalizeOptionalMobile(request.mobile()),
                request.positionId(),
                request.primaryStoreId(),
                request.canService(),
                request.canSell(),
                currentUserId(username)));
    }

    public CreatedResource createWorkstation(CreateWorkstationRequest request, String username) {
        storeDataScope.require(request.storeId());
        return repository.createWorkstation(new NewWorkstation(
                request.storeId(),
                request.code().trim().toUpperCase(Locale.ROOT),
                request.name().trim(),
                request.capacity(),
                request.sortNo(),
                currentUserId(username)));
    }

    public CreatedResource createService(CreateServiceItemRequest request, String username) {
        boolean categoryExists = repository.serviceCategories().stream()
                .anyMatch(category -> category.id() == request.categoryId() && "ACTIVE".equals(category.status()));
        if (!categoryExists) {
            throw new IllegalArgumentException("所选服务分类不存在或已停用");
        }
        List<Long> storeIds = new LinkedHashSet<>(request.storeIds()).stream().toList();
        storeIds.forEach(storeDataScope::require);
        if (request.costAmount().compareTo(request.listPrice()) > 0) {
            throw new IllegalArgumentException("服务成本不能高于标准售价");
        }
        return repository.createService(new NewServiceItem(
                request.code().trim().toUpperCase(Locale.ROOT),
                request.name().trim(),
                request.categoryId(),
                request.durationMinutes(),
                request.costAmount(),
                request.listPrice(),
                request.storePrice(),
                storeIds,
                blankToNull(request.description()),
                currentUserId(username)));
    }

    public ServiceImportOutcome importService(ServiceImportRow row, long storeId, long operatorId) {
        String code = required(row.code(), 64, "项目编号").toUpperCase(Locale.ROOT);
        String name = required(row.name(), 200, "项目名称");
        String categoryCode = required(row.categoryCode(), 64, "分类编号").toUpperCase(Locale.ROOT);
        CategoryOption category = repository.serviceCategories().stream()
                .filter(item -> item.code().equalsIgnoreCase(categoryCode) && "ACTIVE".equals(item.status()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("分类编号不存在或已停用"));
        if (row.durationMinutes() < 5 || row.durationMinutes() > 1440) {
            throw new IllegalArgumentException("服务时长必须在5到1440分钟之间");
        }
        validateAmount(row.costAmount(), "成本");
        validateAmount(row.listPrice(), "标准售价");
        validateAmount(row.storePrice(), "门店售价");
        if (row.costAmount().compareTo(row.listPrice()) > 0) {
            throw new IllegalArgumentException("服务成本不能高于标准售价");
        }
        String description = blankToNull(row.description());
        if (description != null && description.length() > 2000) {
            throw new IllegalArgumentException("项目说明不能超过2000个字符");
        }
        ServiceItemDetail existing = repository.findServiceByCode(code).orElse(null);
        if (existing != null) {
            if (sameImport(existing, name, category.id(), row, description, storeId)) {
                return new ServiceImportOutcome(existing.id(), false, "已存在且内容一致");
            }
            throw new DuplicateResourceException("项目编号已存在且内容不一致");
        }
        CreatedResource created = repository.createService(new NewServiceItem(
                code, name, category.id(), row.durationMinutes(), row.costAmount(), row.listPrice(),
                row.storePrice(), List.of(storeId), description, operatorId));
        return new ServiceImportOutcome(created.id(), true, "已新建");
    }

    public ServiceItemDetail updateService(
            long id, UpdateServiceItemRequest request, String username) {
        ServiceItemDetail current = requireService(id);
        storeDataScope.require(request.storeId());
        if (current.stores().stream().noneMatch(store -> store.storeId() == request.storeId())) {
            throw new IllegalArgumentException("服务项目未配置到所选门店");
        }
        boolean categoryExists = repository.serviceCategories().stream()
                .anyMatch(category -> category.id() == request.categoryId() && "ACTIVE".equals(category.status()));
        if (!categoryExists) throw new IllegalArgumentException("所选服务分类不存在或已停用");
        if (request.costAmount().compareTo(request.listPrice()) > 0) {
            throw new IllegalArgumentException("服务成本不能高于标准售价");
        }
        String status = normalize(request.status(), Set.of("ACTIVE", "DISABLED"), "服务项目状态无效");
        String saleStatus = normalize(request.saleStatus(), Set.of("ON_SALE", "OFF_SALE"), "销售状态无效");
        String description = blankToNull(request.description());
        if (coreChanged(current, request, description, status)) {
            current.stores().forEach(store -> storeDataScope.require(store.storeId()));
        }
        ServiceItemDetail saved = repository.updateService(new ServiceItemUpdate(
                id, request.name().trim(), request.categoryId(), request.durationMinutes(),
                request.costAmount(), request.listPrice(), description, status, request.storeId(),
                request.storePrice(), saleStatus, request.version(), currentUserId(username)));
        return copyWithStores(saved, saved.stores().stream()
                .filter(store -> storeDataScope.canAccess(store.storeId())).toList());
    }

    private ServiceItemDetail requireService(long id) {
        ServiceItemDetail service = repository.findService(id)
                .orElseThrow(() -> new ResourceNotFoundException("服务项目不存在"));
        storeDataScope.requireAny(service.stores().stream().map(ServiceStoreConfig::storeId).toList());
        return service;
    }

    private ServiceItemDetail copyWithStores(
            ServiceItemDetail service, List<ServiceStoreConfig> stores) {
        return new ServiceItemDetail(
                service.id(), service.code(), service.name(), service.categoryId(), service.categoryName(),
                service.durationMinutes(), service.costAmount(), service.listPrice(), service.description(),
                service.status(), stores, service.version());
    }

    private boolean coreChanged(
            ServiceItemDetail current, UpdateServiceItemRequest request, String description, String status) {
        return !current.name().equals(request.name().trim())
                || current.categoryId() != request.categoryId()
                || current.durationMinutes() != request.durationMinutes()
                || current.costAmount().compareTo(request.costAmount()) != 0
                || current.listPrice().compareTo(request.listPrice()) != 0
                || !java.util.Objects.equals(current.description(), description)
                || !current.status().equals(status);
    }

    private boolean sameImport(
            ServiceItemDetail existing,
            String name,
            long categoryId,
            ServiceImportRow row,
            String description,
            long storeId) {
        ServiceStoreConfig store = existing.stores().stream()
                .filter(item -> item.storeId() == storeId).findFirst().orElse(null);
        return store != null && existing.name().equals(name) && existing.categoryId() == categoryId
                && existing.durationMinutes() == row.durationMinutes()
                && existing.costAmount().compareTo(row.costAmount()) == 0
                && existing.listPrice().compareTo(row.listPrice()) == 0
                && store.storePrice().compareTo(row.storePrice()) == 0
                && "ACTIVE".equals(existing.status()) && "ON_SALE".equals(store.saleStatus())
                && java.util.Objects.equals(existing.description(), description);
    }

    private void validateAmount(java.math.BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 15) {
            throw new IllegalArgumentException(field + "必须是最多15位整数、2位小数的非负金额");
        }
    }

    private String required(String value, int maxLength, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) throw new IllegalArgumentException(field + "不能为空");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + "不能超过" + maxLength + "个字符");
        return normalized;
    }

    private long currentUserId(String username) {
        return accessCatalog.userIdentity(username).id();
    }

    private String normalizeOptionalMobile(String mobile) {
        String normalized = blankToNull(mobile);
        if (normalized == null) return null;
        normalized = normalized.replaceAll("[\\s-]", "");
        if (!normalized.matches("1[3-9]\\d{9}")) {
            throw new IllegalArgumentException("员工手机号格式不正确");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalize(String value, Set<String> allowed, String message) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }
}
