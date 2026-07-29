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
    private final List<PositionOption> positions = List.of(
            new PositionOption(1L, "TECHNICIAN", "美甲技师", "ACTIVE"),
            new PositionOption(2L, "STORE_MANAGER", "店长", "ACTIVE"),
            new PositionOption(3L, "RECEPTION", "前台/收银", "ACTIVE"));
    private final List<CategoryOption> categories = List.of(
            new CategoryOption(1L, "NAIL_SERVICE", "美甲服务", "SERVICE", "ACTIVE"));
    private final List<EmployeeSummary> employees = new ArrayList<>(List.of(
            new EmployeeSummary(101L, "E001", "安然", "*******2101", 1L, "美甲技师", 2L,
                    "悦指间示范店", true, true, "ACTIVE"),
            new EmployeeSummary(102L, "E002", "若溪", "*******2102", 1L, "美甲技师", 2L,
                    "悦指间示范店", true, true, "ACTIVE")));
    private final List<WorkstationSummary> workstations = new ArrayList<>(List.of(
            new WorkstationSummary(201L, 2L, "悦指间示范店", "W01", "一号美甲台", 1, 10, "ACTIVE"),
            new WorkstationSummary(202L, 2L, "悦指间示范店", "W02", "二号美甲台", 1, 20, "ACTIVE")));
    private final Map<Long, ServiceItemDetail> services = new LinkedHashMap<>();
    private final AtomicLong employeeIds = new AtomicLong(102);
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
    public List<PositionOption> positions() {
        return positions;
    }

    @Override
    public List<CategoryOption> serviceCategories() {
        return categories;
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
    public synchronized List<WorkstationSummary> workstations(Long storeId) {
        return workstations.stream()
                .filter(workstation -> storeId == null || workstation.storeId() == storeId)
                .sorted(Comparator.comparingInt(WorkstationSummary::sortNo))
                .toList();
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
    public synchronized CreatedResource createEmployee(NewEmployee employee) {
        long id = employeeIds.incrementAndGet();
        String positionName = positions.stream().filter(position -> position.id() == employee.positionId())
                .findFirst().map(PositionOption::name).orElse("未知职务");
        employees.add(new EmployeeSummary(
                id, employee.employeeNo(), employee.name(), mask(employee.mobile()), employee.positionId(),
                positionName, employee.primaryStoreId(), storeName(employee.primaryStoreId()),
                employee.canService(), employee.canSell(), "ACTIVE"));
        return new CreatedResource(id);
    }

    @Override
    public synchronized CreatedResource createWorkstation(NewWorkstation workstation) {
        long id = workstationIds.incrementAndGet();
        workstations.add(new WorkstationSummary(
                id, workstation.storeId(), storeName(workstation.storeId()), workstation.code(), workstation.name(),
                workstation.capacity(), workstation.sortNo(), "ACTIVE"));
        return new CreatedResource(id);
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

    private String storeName(long storeId) {
        return storeId == 1L ? "悦指间总部" : "悦指间示范店";
    }
}
