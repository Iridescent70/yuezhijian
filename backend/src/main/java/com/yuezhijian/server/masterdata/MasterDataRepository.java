package com.yuezhijian.server.masterdata;

import java.util.List;
import java.util.Optional;

public interface MasterDataRepository {
    List<PositionOption> positions();

    List<CategoryOption> serviceCategories();

    List<EmployeeSummary> employees(Long storeId, String keyword);

    List<WorkstationSummary> workstations(Long storeId);

    List<ServiceItemSummary> services(Long storeId, String keyword);

    Optional<ServiceItemDetail> findService(long id);

    CreatedResource createEmployee(NewEmployee employee);

    CreatedResource createWorkstation(NewWorkstation workstation);

    CreatedResource createService(NewServiceItem service);

    ServiceItemDetail updateService(ServiceItemUpdate update);
}
