package com.yuezhijian.server.masterdata;

import java.util.List;
import java.util.Optional;

public interface MasterDataRepository {
    List<PositionOption> positions(boolean activeOnly);

    Optional<PositionOption> findPosition(long id);

    List<CategoryOption> categories(String type, boolean activeOnly);

    default List<CategoryOption> categories(String type) {
        return categories(type, true);
    }

    Optional<CategoryOption> findCategory(long id);

    List<UnitOption> units(boolean activeOnly);

    default List<UnitOption> units() {
        return units(true);
    }

    Optional<UnitOption> findUnit(long id);

    List<EmployeeSummary> employees(Long storeId, String keyword);

    Optional<EmployeeSummary> findEmployee(long id);

    List<WorkstationSummary> workstations(Long storeId);

    Optional<WorkstationSummary> findWorkstation(long id);

    List<ServiceItemSummary> services(Long storeId, String keyword);

    Optional<ServiceItemDetail> findService(long id);

    Optional<ServiceItemDetail> findServiceByCode(String code);

    CreatedResource createPosition(NewPosition position);

    PositionOption updatePosition(PositionUpdate update);

    CreatedResource createCategory(NewCategory category);

    CategoryOption updateCategory(CategoryUpdate update);

    CreatedResource createUnit(NewUnit unit);

    UnitOption updateUnit(UnitUpdate update);

    CreatedResource createEmployee(NewEmployee employee);

    EmployeeSummary updateEmployee(EmployeeUpdate update);

    CreatedResource createWorkstation(NewWorkstation workstation);

    WorkstationSummary updateWorkstation(WorkstationUpdate update);

    CreatedResource createService(NewServiceItem service);

    ServiceItemDetail updateService(ServiceItemUpdate update);
}
