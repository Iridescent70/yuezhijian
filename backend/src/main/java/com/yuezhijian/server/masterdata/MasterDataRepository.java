package com.yuezhijian.server.masterdata;

import java.util.List;

public interface MasterDataRepository {
    List<PositionOption> positions();

    List<CategoryOption> serviceCategories();

    List<EmployeeSummary> employees(Long storeId, String keyword);

    List<WorkstationSummary> workstations(Long storeId);

    List<ServiceItemSummary> services(Long storeId, String keyword);

    CreatedResource createEmployee(NewEmployee employee);

    CreatedResource createWorkstation(NewWorkstation workstation);

    CreatedResource createService(NewServiceItem service);
}
