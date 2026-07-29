package com.yuezhijian.server.masterdata;

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
