package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;
import java.util.ArrayList;
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
public class MemoryMasterDataRepository implements MasterDataRepository {
    private final List<PositionOption> positions = new ArrayList<>(List.of(
            new PositionOption(1L, "TECHNICIAN", "美甲技师", 10, BigDecimal.ZERO, BigDecimal.ZERO, "ACTIVE", "1"),
            new PositionOption(2L, "STORE_MANAGER", "店长", 20, BigDecimal.ZERO, BigDecimal.ZERO, "ACTIVE", "1"),
            new PositionOption(3L, "RECEPTION", "前台/收银", 5, BigDecimal.ZERO, BigDecimal.ZERO, "ACTIVE", "1")));
    private final List<CategoryOption> categories = new ArrayList<>(List.of(
            new CategoryOption(1L, "NAIL_SERVICE", "美甲服务", "SERVICE", 10, "ACTIVE", "1"),
            new CategoryOption(2L, "RETAIL_PRODUCT", "零售产品", "PRODUCT", 10, "ACTIVE", "1"),
            new CategoryOption(3L, "POINT_GIFT", "积分礼品", "GIFT", 10, "ACTIVE", "1")));
    private final List<UnitOption> units = new ArrayList<>(List.of(
            new UnitOption(1L, "TIME", "次", 0, "ACTIVE", "1"),
            new UnitOption(2L, "PIECE", "件", 0, "ACTIVE", "1"),
            new UnitOption(3L, "BOTTLE", "瓶", 2, "ACTIVE", "1")));
    private final List<EmployeeSummary> employees = new ArrayList<>(List.of(
            new EmployeeSummary(101L, "E001", "安然", "*******2101", 1L, "美甲技师", 2L,
                    "悦指间示范店", java.time.LocalDate.of(2025, 1, 1), null,
                    true, true, "ACTIVE", "1"),
            new EmployeeSummary(102L, "E002", "若溪", "*******2102", 1L, "美甲技师", 2L,
                    "悦指间示范店", java.time.LocalDate.of(2025, 2, 1), null,
                    true, true, "ACTIVE", "1")));
    private final List<WorkstationSummary> workstations = new ArrayList<>(List.of(
            new WorkstationSummary(201L, 2L, "悦指间示范店", "W01", "一号美甲台", 1, 10, "ACTIVE", "1"),
            new WorkstationSummary(202L, 2L, "悦指间示范店", "W02", "二号美甲台", 1, 20, "ACTIVE", "1")));
    private final Map<Long, ServiceItemDetail> services = new LinkedHashMap<>();
    private final AtomicLong employeeIds = new AtomicLong(102);
    private final AtomicLong positionIds = new AtomicLong(3);
    private final AtomicLong categoryIds = new AtomicLong(3);
    private final AtomicLong unitIds = new AtomicLong(3);
    private final AtomicLong workstationIds = new AtomicLong(202);
    private final AtomicLong serviceIds = new AtomicLong(302);

    public MemoryMasterDataRepository() {
        services.put(301L, new ServiceItemDetail(
                301L, "SVC001", "基础单色美甲", 1L, "美甲服务", 60,
                new BigDecimal("30.00"), new BigDecimal("168.00"), "基础单色服务", "ACTIVE",
                List.of(new ServiceStoreConfig(
                        2L, "悦指间示范店", new BigDecimal("168.00"), "ON_SALE")), "1"));
        services.put(302L, new ServiceItemDetail(
                302L, "SVC002", "精致款式美甲", 1L, "美甲服务", 120,
                new BigDecimal("60.00"), new BigDecimal("298.00"), "精致款式服务", "ACTIVE",
                List.of(new ServiceStoreConfig(
                        2L, "悦指间示范店", new BigDecimal("298.00"), "ON_SALE")), "1"));
    }

    @Override
    public synchronized List<PositionOption> positions(boolean activeOnly) {
        return positions.stream()
                .filter(position -> !activeOnly || "ACTIVE".equals(position.status()))
                .sorted(Comparator.comparingInt(PositionOption::level).reversed()
                        .thenComparingLong(PositionOption::id))
                .toList();
    }

    @Override
    public synchronized Optional<PositionOption> findPosition(long id) {
        return positions.stream().filter(position -> position.id() == id).findFirst();
    }

    @Override
    public synchronized List<CategoryOption> categories(String type, boolean activeOnly) {
        return categories.stream()
                .filter(category -> category.type().equals(type))
                .filter(category -> !activeOnly || "ACTIVE".equals(category.status()))
                .sorted(Comparator.comparingInt(CategoryOption::sortNo).thenComparingLong(CategoryOption::id))
                .toList();
    }

    @Override
    public synchronized Optional<CategoryOption> findCategory(long id) {
        return categories.stream().filter(category -> category.id() == id).findFirst();
    }

    @Override
    public synchronized List<UnitOption> units(boolean activeOnly) {
        return units.stream()
                .filter(unit -> !activeOnly || "ACTIVE".equals(unit.status()))
                .sorted(Comparator.comparingLong(UnitOption::id))
                .toList();
    }

    @Override
    public synchronized Optional<UnitOption> findUnit(long id) {
        return units.stream().filter(unit -> unit.id() == id).findFirst();
    }

    @Override
    public synchronized List<EmployeeSummary> employees(Long storeId, String keyword) {
        String normalized = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return employees.stream()
                .filter(employee -> storeId == null || java.util.Objects.equals(employee.storeId(), storeId))
                .filter(employee -> normalized == null
                        || employee.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || employee.employeeNo().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparingLong(EmployeeSummary::id).reversed())
                .toList();
    }

    @Override
    public synchronized Optional<EmployeeSummary> findEmployee(long id) {
        return employees.stream().filter(employee -> employee.id() == id).findFirst();
    }

    @Override
    public synchronized List<WorkstationSummary> workstations(Long storeId) {
        return workstations.stream()
                .filter(workstation -> storeId == null || workstation.storeId() == storeId)
                .sorted(Comparator.comparingInt(WorkstationSummary::sortNo))
                .toList();
    }

    @Override
    public synchronized Optional<WorkstationSummary> findWorkstation(long id) {
        return workstations.stream().filter(workstation -> workstation.id() == id).findFirst();
    }

    @Override
    public synchronized List<ServiceItemSummary> services(Long storeId, String keyword) {
        String normalized = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return services.values().stream()
                .filter(service -> storeId == null || service.stores().stream()
                        .anyMatch(store -> store.storeId() == storeId))
                .filter(service -> normalized == null
                        || service.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || service.code().toLowerCase(Locale.ROOT).contains(normalized))
                .map(service -> summary(service, storeId))
                .sorted(Comparator.comparingLong(ServiceItemSummary::id).reversed())
                .toList();
    }

    @Override
    public synchronized Optional<ServiceItemDetail> findService(long id) {
        return Optional.ofNullable(services.get(id));
    }

    @Override
    public synchronized Optional<ServiceItemDetail> findServiceByCode(String code) {
        return services.values().stream().filter(service -> service.code().equals(code)).findFirst();
    }

    @Override
    public synchronized CreatedResource createPosition(NewPosition position) {
        if (positions.stream().anyMatch(item -> item.code().equalsIgnoreCase(position.code()))) {
            throw new com.yuezhijian.server.common.DuplicateResourceException("职务编号已存在");
        }
        long id = positionIds.incrementAndGet();
        positions.add(new PositionOption(
                id, position.code(), position.name(), position.level(), position.defaultServiceRate(),
                position.defaultSalesRate(), "ACTIVE", "1"));
        return new CreatedResource(id);
    }

    @Override
    public synchronized PositionOption updatePosition(PositionUpdate update) {
        PositionOption current = findPosition(update.id()).orElse(null);
        if (current == null || !current.version().equals(update.version())) throw stale("职务");
        PositionOption saved = new PositionOption(
                current.id(), current.code(), update.name(), update.level(), update.defaultServiceRate(),
                update.defaultSalesRate(), update.status(), nextVersion(current.version()));
        positions.set(positions.indexOf(current), saved);
        return saved;
    }

    @Override
    public synchronized CreatedResource createCategory(NewCategory category) {
        if (categories.stream().anyMatch(item -> item.type().equalsIgnoreCase(category.type())
                && item.code().equalsIgnoreCase(category.code()))) {
            throw new com.yuezhijian.server.common.DuplicateResourceException("分类编号已存在");
        }
        long id = categoryIds.incrementAndGet();
        categories.add(new CategoryOption(
                id, category.code(), category.name(), category.type(), category.sortNo(), "ACTIVE", "1"));
        return new CreatedResource(id);
    }

    @Override
    public synchronized CategoryOption updateCategory(CategoryUpdate update) {
        CategoryOption current = findCategory(update.id()).orElse(null);
        if (current == null || !current.version().equals(update.version())) throw stale("分类");
        CategoryOption saved = new CategoryOption(
                current.id(), current.code(), update.name(), current.type(), update.sortNo(),
                update.status(), nextVersion(current.version()));
        categories.set(categories.indexOf(current), saved);
        return saved;
    }

    @Override
    public synchronized CreatedResource createUnit(NewUnit unit) {
        if (units.stream().anyMatch(item -> item.code().equalsIgnoreCase(unit.code()))) {
            throw new com.yuezhijian.server.common.DuplicateResourceException("单位编号已存在");
        }
        long id = unitIds.incrementAndGet();
        units.add(new UnitOption(id, unit.code(), unit.name(), unit.decimalPlaces(), "ACTIVE", "1"));
        return new CreatedResource(id);
    }

    @Override
    public synchronized UnitOption updateUnit(UnitUpdate update) {
        UnitOption current = findUnit(update.id()).orElse(null);
        if (current == null || !current.version().equals(update.version())) throw stale("单位");
        UnitOption saved = new UnitOption(
                current.id(), current.code(), update.name(), update.decimalPlaces(), update.status(),
                nextVersion(current.version()));
        units.set(units.indexOf(current), saved);
        return saved;
    }

    @Override
    public synchronized CreatedResource createEmployee(NewEmployee employee) {
        long id = employeeIds.incrementAndGet();
        String positionName = positions.stream().filter(position -> position.id() == employee.positionId())
                .findFirst().map(PositionOption::name).orElse("未知职务");
        employees.add(new EmployeeSummary(
                id, employee.employeeNo(), employee.name(), mask(employee.mobile()), employee.positionId(),
                positionName, employee.primaryStoreId(), storeName(employee.primaryStoreId()),
                employee.hireDate(), null, employee.canService(), employee.canSell(), "ACTIVE", "1"));
        return new CreatedResource(id);
    }

    @Override
    public synchronized EmployeeSummary updateEmployee(EmployeeUpdate update) {
        EmployeeSummary current = findEmployee(update.id()).orElse(null);
        if (current == null || !current.version().equals(update.version())) {
            throw stale("员工");
        }
        String positionName = positions.stream().filter(position -> position.id() == update.positionId())
                .findFirst().map(PositionOption::name).orElse("未知职务");
        EmployeeSummary saved = new EmployeeSummary(
                current.id(), current.employeeNo(), update.name(),
                update.mobile() == null ? current.maskedMobile() : mask(update.mobile()),
                update.positionId(), positionName, update.primaryStoreId(), storeName(update.primaryStoreId()),
                update.hireDate(), update.leaveDate(), update.canService(), update.canSell(), update.status(),
                nextVersion(current.version()));
        employees.set(employees.indexOf(current), saved);
        return saved;
    }

    @Override
    public synchronized CreatedResource createWorkstation(NewWorkstation workstation) {
        long id = workstationIds.incrementAndGet();
        workstations.add(new WorkstationSummary(
                id, workstation.storeId(), storeName(workstation.storeId()), workstation.code(), workstation.name(),
                workstation.capacity(), workstation.sortNo(), "ACTIVE", "1"));
        return new CreatedResource(id);
    }

    @Override
    public synchronized WorkstationSummary updateWorkstation(WorkstationUpdate update) {
        WorkstationSummary current = findWorkstation(update.id()).orElse(null);
        if (current == null || !current.version().equals(update.version())) {
            throw stale("工位");
        }
        WorkstationSummary saved = new WorkstationSummary(
                current.id(), current.storeId(), current.storeName(), current.code(), update.name(),
                update.capacity(), update.sortNo(), update.status(), nextVersion(current.version()));
        workstations.set(workstations.indexOf(current), saved);
        return saved;
    }

    @Override
    public synchronized CreatedResource createService(NewServiceItem service) {
        if (findServiceByCode(service.code()).isPresent()) {
            throw new com.yuezhijian.server.common.DuplicateResourceException("服务项目编号已存在");
        }
        long id = serviceIds.incrementAndGet();
        String categoryName = categories.stream().filter(category -> category.id() == service.categoryId())
                .findFirst().map(CategoryOption::name).orElse("未分类");
        List<ServiceStoreConfig> stores = service.storeIds().stream()
                .map(storeId -> new ServiceStoreConfig(
                        storeId, storeName(storeId), service.storePrice(), "ON_SALE"))
                .toList();
        services.put(id, new ServiceItemDetail(
                id, service.code(), service.name(), service.categoryId(), categoryName, service.durationMinutes(),
                service.costAmount(), service.listPrice(), service.description(), "ACTIVE", stores, "1"));
        return new CreatedResource(id);
    }

    @Override
    public synchronized ServiceItemDetail updateService(ServiceItemUpdate update) {
        ServiceItemDetail current = services.get(update.id());
        if (current == null || !current.version().equals(update.version())) {
            throw new com.yuezhijian.server.common.DuplicateResourceException(
                    "服务项目已被他人修改，请刷新后重试");
        }
        String categoryName = categories.stream().filter(category -> category.id() == update.categoryId())
                .findFirst().map(CategoryOption::name).orElse("未分类");
        boolean storeFound = current.stores().stream().anyMatch(store -> store.storeId() == update.storeId());
        if (!storeFound) throw new IllegalArgumentException("服务项目未配置到所选门店");
        List<ServiceStoreConfig> stores = current.stores().stream()
                .map(store -> store.storeId() == update.storeId()
                        ? new ServiceStoreConfig(
                                store.storeId(), store.storeName(), update.storePrice(), update.saleStatus())
                        : store)
                .toList();
        ServiceItemDetail saved = new ServiceItemDetail(
                current.id(), current.code(), update.name(), update.categoryId(), categoryName,
                update.durationMinutes(), update.costAmount(), update.listPrice(), update.description(),
                update.status(), stores, nextVersion(current.version()));
        services.put(saved.id(), saved);
        return saved;
    }

    private ServiceItemSummary summary(ServiceItemDetail service, Long storeId) {
        ServiceStoreConfig store = service.stores().stream()
                .filter(item -> storeId == null || item.storeId() == storeId)
                .findFirst().orElseThrow();
        return new ServiceItemSummary(
                service.id(), service.code(), service.name(), service.categoryId(), service.categoryName(),
                service.durationMinutes(), service.costAmount(), service.listPrice(), store.storePrice(),
                store.saleStatus(), service.status());
    }

    private String nextVersion(String version) {
        return String.valueOf(Long.parseLong(version) + 1);
    }

    private String mask(String mobile) {
        return mobile == null ? null : "*******" + mobile.substring(mobile.length() - 4);
    }

    private com.yuezhijian.server.common.DuplicateResourceException stale(String resource) {
        return new com.yuezhijian.server.common.DuplicateResourceException(
                resource + "资料已被他人修改，请刷新后重试");
    }

    private String storeName(long storeId) {
        return storeId == 1L ? "悦指间总部" : "悦指间示范店";
    }
}
