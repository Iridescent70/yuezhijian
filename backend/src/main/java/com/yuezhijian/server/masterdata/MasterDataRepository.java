package com.yuezhijian.server.masterdata;

import java.util.List;
import java.util.Optional;

public interface MasterDataRepository {
    List<PositionOption> positions();

    List<CategoryOption> categories(String type);

    List<UnitOption> units();

    List<EmployeeSummary> employees(Long storeId, String keyword);

    Optional<EmployeeSummary> findEmployee(long id);

    List<WorkstationSummary> workstations(Long storeId);

    Optional<WorkstationSummary> findWorkstation(long id);

    List<ServiceItemSummary> services(Long storeId, String keyword);

    Optional<ServiceItemDetail> findService(long id);

    Optional<ServiceItemDetail> findServiceByCode(String code);

    CreatedResource createEmployee(NewEmployee employee);

    EmployeeSummary updateEmployee(EmployeeUpdate update);

    CreatedResource createWorkstation(NewWorkstation workstation);

    WorkstationSummary updateWorkstation(WorkstationUpdate update);

    CreatedResource createService(NewServiceItem service);

    ServiceItemDetail updateService(ServiceItemUpdate update);
}
