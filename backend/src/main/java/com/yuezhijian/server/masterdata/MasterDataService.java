package com.yuezhijian.server.masterdata;

import com.yuezhijian.server.audit.AuditService;
import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreDataScope;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterDataService {
    private final MasterDataRepository repository;
    private final AccessCatalogService accessCatalog;
    private final StoreDataScope storeDataScope;
    private final AuditService auditService;

    public MasterDataService(
            MasterDataRepository repository,
            AccessCatalogService accessCatalog,
            StoreDataScope storeDataScope,
            AuditService auditService) {
        this.repository = repository;
        this.accessCatalog = accessCatalog;
        this.storeDataScope = storeDataScope;
        this.auditService = auditService;
    }

    public List<PositionOption> positions() {
        return positions(true);
    }

    public List<PositionOption> positions(boolean activeOnly) {
        return repository.positions(activeOnly);
    }

    public PositionOption position(long id) {
        return repository.findPosition(id)
                .orElseThrow(() -> new ResourceNotFoundException("职务不存在"));
    }

    public List<CategoryOption> serviceCategories() {
        return repository.categories("SERVICE", true);
    }

    public List<CategoryOption> itemCategories(String type) {
        return itemCategories(type, true);
    }

    public List<CategoryOption> itemCategories(String type, boolean activeOnly) {
        String normalized = normalize(type, Set.of("SERVICE", "PRODUCT"), "分类类型无效");
        return repository.categories(normalized, activeOnly);
    }

    public CategoryOption itemCategory(long id) {
        return repository.findCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("分类不存在"));
    }

    public List<UnitOption> units() {
        return units(true);
    }

    public List<UnitOption> units(boolean activeOnly) {
        return repository.units(activeOnly);
    }

    public UnitOption unit(long id) {
        return repository.findUnit(id)
                .orElseThrow(() -> new ResourceNotFoundException("单位不存在"));
    }

    public List<EmployeeSummary> employees(Long storeId, String keyword) {
        return repository.employees(storeDataScope.constrainNullable(storeId), blankToNull(keyword));
    }

    public EmployeeSummary employee(long id) {
        EmployeeSummary employee = repository.findEmployee(id)
                .orElseThrow(() -> new ResourceNotFoundException("员工不存在"));
        storeDataScope.require(employee.storeId());
        return employee;
    }

    public List<WorkstationSummary> workstations(Long storeId) {
        return repository.workstations(storeDataScope.constrainNullable(storeId));
    }

    public WorkstationSummary workstation(long id) {
        WorkstationSummary workstation = repository.findWorkstation(id)
                .orElseThrow(() -> new ResourceNotFoundException("工位不存在"));
        storeDataScope.require(workstation.storeId());
        return workstation;
    }

    public List<ServiceItemSummary> services(Long storeId, String keyword) {
        return repository.services(storeDataScope.constrainNullable(storeId), blankToNull(keyword));
    }

    public ServiceItemDetail service(long id) {
        ServiceItemDetail service = requireService(id);
        return copyWithStores(service, service.stores().stream()
                .filter(store -> storeDataScope.canAccess(store.storeId())).toList());
    }

    public CreatedResource createPosition(CreatePositionRequest request, String username) {
        return repository.createPosition(new NewPosition(
                request.code().trim().toUpperCase(Locale.ROOT), request.name().trim(), request.level(),
                normalizedRate(request.defaultServiceRate()), normalizedRate(request.defaultSalesRate()),
                currentUserId(username)));
    }

    public PositionOption updatePosition(long id, UpdatePositionRequest request, String username) {
        PositionOption current = position(id);
        String status = normalize(request.status(), Set.of("ACTIVE", "DISABLED"), "职务状态无效");
        return repository.updatePosition(new PositionUpdate(
                current.id(), request.name().trim(), request.level(),
                normalizedRate(request.defaultServiceRate()), normalizedRate(request.defaultSalesRate()),
                status, request.version(), currentUserId(username)));
    }

    public CreatedResource createCategory(CreateCategoryRequest request, String username) {
        String type = normalize(request.type(), Set.of("SERVICE", "PRODUCT"), "分类类型无效");
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        return repository.createCategory(new NewCategory(
                type, code, request.name().trim(), "/" + type + "/" + code + "/",
                request.sortNo(), currentUserId(username)));
    }

    public CategoryOption updateCategory(long id, UpdateCategoryRequest request, String username) {
        CategoryOption current = itemCategory(id);
        String status = normalize(request.status(), Set.of("ACTIVE", "DISABLED"), "分类状态无效");
        return repository.updateCategory(new CategoryUpdate(
                current.id(), request.name().trim(), request.sortNo(), status,
                request.version(), currentUserId(username)));
    }

    public CreatedResource createUnit(CreateUnitRequest request, String username) {
        return repository.createUnit(new NewUnit(
                request.code().trim().toUpperCase(Locale.ROOT), request.name().trim(),
                request.decimalPlaces(), currentUserId(username)));
    }

    public UnitOption updateUnit(long id, UpdateUnitRequest request, String username) {
        UnitOption current = unit(id);
        String status = normalize(request.status(), Set.of("ACTIVE", "DISABLED"), "单位状态无效");
        return repository.updateUnit(new UnitUpdate(
                current.id(), request.name().trim(), request.decimalPlaces(), status,
                request.version(), currentUserId(username)));
    }

    public CreatedResource createEmployee(CreateEmployeeRequest request, String username) {
        storeDataScope.require(request.primaryStoreId());
        requireActivePosition(request.positionId());
        return repository.createEmployee(new NewEmployee(
                request.employeeNo().trim().toUpperCase(Locale.ROOT),
                request.name().trim(),
                normalizeOptionalMobile(request.mobile()),
                request.positionId(),
                request.primaryStoreId(),
                request.hireDate(),
                request.canService(),
                request.canSell(),
                currentUserId(username)));
    }

    public EmployeeSummary updateEmployee(long id, UpdateEmployeeRequest request, String username) {
        EmployeeSummary current = employee(id);
        storeDataScope.require(request.primaryStoreId());
        requireActivePosition(request.positionId());
        String status = normalize(request.status(), Set.of("ACTIVE", "DISABLED", "LEFT"), "员工状态无效");
        validateEmploymentDates(request.hireDate(), request.leaveDate(), status);
        String mobile = request.mobile() == null || request.mobile().isBlank()
                ? null : normalizeOptionalMobile(request.mobile());
        EmployeeSummary saved = repository.updateEmployee(new EmployeeUpdate(
                current.id(), request.name().trim(), mobile, request.positionId(), request.primaryStoreId(),
                request.hireDate(), request.leaveDate(), request.canService(), request.canSell(), status,
                request.version(), currentUserId(username)));
        storeDataScope.require(saved.storeId());
        return saved;
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

    public WorkstationSummary updateWorkstation(
            long id, UpdateWorkstationRequest request, String username) {
        WorkstationSummary current = workstation(id);
        String status = normalize(request.status(), Set.of("ACTIVE", "DISABLED"), "工位状态无效");
        return repository.updateWorkstation(new WorkstationUpdate(
                current.id(), request.name().trim(), request.capacity(), request.sortNo(), status,
                request.version(), currentUserId(username)));
    }

    @Transactional
    public CreatedResource createService(CreateServiceItemRequest request, String username) {
        boolean categoryExists = repository.categories("SERVICE").stream()
                .anyMatch(category -> category.id() == request.categoryId() && "ACTIVE".equals(category.status()));
        if (!categoryExists) {
            throw new IllegalArgumentException("所选服务分类不存在或已停用");
        }
        List<Long> storeIds = new LinkedHashSet<>(request.storeIds()).stream().toList();
        storeIds.forEach(storeDataScope::require);
        if (request.costAmount().compareTo(request.listPrice()) > 0) {
            throw new IllegalArgumentException("服务成本不能高于标准售价");
        }
        long operatorId = currentUserId(username);
        CreatedResource created = repository.createService(new NewServiceItem(
                request.code().trim().toUpperCase(Locale.ROOT),
                request.name().trim(),
                request.categoryId(),
                request.durationMinutes(),
                request.costAmount(),
                request.listPrice(),
                request.storePrice(),
                storeIds,
                blankToNull(request.description()),
                operatorId));
        ServiceItemDetail detail = repository.findService(created.id()).orElseThrow();
        auditService.record("CATALOG", "CREATE", "SERVICE", created.id(), null, null,
                serviceSnapshot(detail, null), operatorId);
        return created;
    }

    @Transactional
    public ServiceImportOutcome importService(ServiceImportRow row, long storeId, long operatorId) {
        String code = required(row.code(), 64, "项目编号").toUpperCase(Locale.ROOT);
        String name = required(row.name(), 200, "项目名称");
        String categoryCode = required(row.categoryCode(), 64, "分类编号").toUpperCase(Locale.ROOT);
        CategoryOption category = repository.categories("SERVICE").stream()
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
        ServiceItemDetail detail = repository.findService(created.id()).orElseThrow();
        auditService.record("CATALOG", "IMPORT_CREATE", "SERVICE", created.id(), storeId, null,
                serviceSnapshot(detail, storeId), operatorId);
        return new ServiceImportOutcome(created.id(), true, "已新建");
    }

    @Transactional
    public ServiceItemDetail updateService(
            long id, UpdateServiceItemRequest request, String username) {
        ServiceItemDetail current = requireService(id);
        storeDataScope.require(request.storeId());
        if (current.stores().stream().noneMatch(store -> store.storeId() == request.storeId())) {
            throw new IllegalArgumentException("服务项目未配置到所选门店");
        }
        boolean categoryExists = repository.categories("SERVICE").stream()
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
        long operatorId = currentUserId(username);
        ServiceItemDetail saved = repository.updateService(new ServiceItemUpdate(
                id, request.name().trim(), request.categoryId(), request.durationMinutes(),
                request.costAmount(), request.listPrice(), description, status, request.storeId(),
                request.storePrice(), saleStatus, request.version(), operatorId));
        auditService.record("CATALOG", "UPDATE", "SERVICE", id, request.storeId(),
                serviceSnapshot(current, request.storeId()), serviceSnapshot(saved, request.storeId()), operatorId);
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

    private Map<String, Object> serviceSnapshot(ServiceItemDetail item, Long storeId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", item.code());
        snapshot.put("name", item.name());
        snapshot.put("categoryName", item.categoryName());
        snapshot.put("durationMinutes", item.durationMinutes());
        snapshot.put("costAmount", item.costAmount());
        snapshot.put("listPrice", item.listPrice());
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

    private void requireActivePosition(long positionId) {
        boolean positionExists = repository.positions(true).stream()
                .anyMatch(position -> position.id() == positionId && "ACTIVE".equals(position.status()));
        if (!positionExists) throw new IllegalArgumentException("所选职务不存在或已停用");
    }

    private java.math.BigDecimal normalizedRate(java.math.BigDecimal rate) {
        return rate.setScale(6, java.math.RoundingMode.HALF_UP);
    }

    private void validateEmploymentDates(
            java.time.LocalDate hireDate, java.time.LocalDate leaveDate, String status) {
        if (hireDate != null && leaveDate != null && leaveDate.isBefore(hireDate)) {
            throw new IllegalArgumentException("离职日期不能早于入职日期");
        }
        if ("LEFT".equals(status) && leaveDate == null) {
            throw new IllegalArgumentException("离职员工必须填写离职日期");
        }
        if (!"LEFT".equals(status) && leaveDate != null) {
            throw new IllegalArgumentException("填写离职日期时员工状态必须为离职");
        }
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
