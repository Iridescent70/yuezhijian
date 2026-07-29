package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final List<ServiceItemSummary> services = new ArrayList<>(List.of(
            new ServiceItemSummary(301L, "SVC001", "基础单色美甲", 1L, "美甲服务", 60,
                    new BigDecimal("30.00"), new BigDecimal("168.00"), new BigDecimal("168.00"),
                    "ON_SALE", "ACTIVE"),
            new ServiceItemSummary(302L, "SVC002", "精致款式美甲", 1L, "美甲服务", 120,
                    new BigDecimal("60.00"), new BigDecimal("298.00"), new BigDecimal("298.00"),
                    "ON_SALE", "ACTIVE")));
    private final Map<Long, List<Long>> serviceStores = new ConcurrentHashMap<>(Map.of(
            301L, List.of(2L),
            302L, List.of(2L)));
    private final AtomicLong employeeIds = new AtomicLong(102);
    private final AtomicLong workstationIds = new AtomicLong(202);
    private final AtomicLong serviceIds = new AtomicLong(302);

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
        return services.stream()
                .filter(service -> storeId == null
                        || serviceStores.getOrDefault(service.id(), List.of()).contains(storeId))
                .filter(service -> normalized == null
                        || service.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || service.code().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparingLong(ServiceItemSummary::id).reversed())
                .toList();
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
        long id = serviceIds.incrementAndGet();
        String categoryName = categories.stream().filter(category -> category.id() == service.categoryId())
                .findFirst().map(CategoryOption::name).orElse("未分类");
        services.add(new ServiceItemSummary(
                id, service.code(), service.name(), service.categoryId(), categoryName, service.durationMinutes(),
                service.costAmount(), service.listPrice(), service.storePrice(), "ON_SALE", "ACTIVE"));
        serviceStores.put(id, List.copyOf(service.storeIds()));
        return new CreatedResource(id);
    }

    private String mask(String mobile) {
        return mobile == null ? null : "*******" + mobile.substring(mobile.length() - 4);
    }

    private String storeName(long storeId) {
        return storeId == 1L ? "悦指间总部" : "悦指间示范店";
    }
}
