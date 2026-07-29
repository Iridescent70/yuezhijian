package com.yuezhijian.server.masterdata;

import com.yuezhijian.server.common.SensitiveDataCodec;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlserver")
public class SqlServerMasterDataRepository implements MasterDataRepository {
    private final MasterDataMapper mapper;
    private final SensitiveDataCodec codec;

    public SqlServerMasterDataRepository(MasterDataMapper mapper, SensitiveDataCodec codec) {
        this.mapper = mapper;
        this.codec = codec;
    }

    @Override
    public List<PositionOption> positions() {
        return mapper.findPositions();
    }

    @Override
    public List<CategoryOption> serviceCategories() {
        return mapper.findServiceCategories();
    }

    @Override
    public List<EmployeeSummary> employees(Long storeId, String keyword) {
        return mapper.findEmployees(storeId, keyword);
    }

    @Override
    public List<WorkstationSummary> workstations(Long storeId) {
        return mapper.findWorkstations(storeId);
    }

    @Override
    public List<ServiceItemSummary> services(Long storeId, String keyword) {
        return mapper.findServices(storeId, keyword);
    }

    @Override
    public CreatedResource createEmployee(NewEmployee employee) {
        String ciphertext = employee.mobile() == null ? null : codec.encrypt(employee.mobile());
        String hash = employee.mobile() == null ? null : codec.searchableHash(employee.mobile());
        String last4 = employee.mobile() == null ? null
                : employee.mobile().substring(employee.mobile().length() - 4);
        long id = mapper.insertEmployee(new ProtectedEmployeeRow(
                employee.employeeNo(), employee.name(), ciphertext, hash, last4,
                employee.positionId(), employee.primaryStoreId(), employee.canService(), employee.canSell(),
                employee.createdBy()));
        return new CreatedResource(id);
    }

    @Override
    public CreatedResource createWorkstation(NewWorkstation workstation) {
        return new CreatedResource(mapper.insertWorkstation(workstation));
    }

    @Override
    @Transactional
    public CreatedResource createService(NewServiceItem service) {
        long id = mapper.insertService(service);
        service.storeIds().forEach(storeId -> mapper.insertServiceStore(
                id, storeId, service.storePrice(), service.createdBy()));
        return new CreatedResource(id);
    }
}
