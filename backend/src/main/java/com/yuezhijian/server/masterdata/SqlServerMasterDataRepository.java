package com.yuezhijian.server.masterdata;

import com.yuezhijian.server.common.SensitiveDataCodec;
import java.util.List;
import java.util.Optional;
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
    public List<CategoryOption> categories(String type) {
        return mapper.findCategories(type);
    }

    @Override
    public List<UnitOption> units() {
        return mapper.findUnits();
    }

    @Override
    public List<EmployeeSummary> employees(Long storeId, String keyword) {
        return mapper.findEmployees(storeId, keyword);
    }

    @Override
    public Optional<EmployeeSummary> findEmployee(long id) {
        return Optional.ofNullable(mapper.findEmployee(id));
    }

    @Override
    public List<WorkstationSummary> workstations(Long storeId) {
        return mapper.findWorkstations(storeId);
    }

    @Override
    public Optional<WorkstationSummary> findWorkstation(long id) {
        return Optional.ofNullable(mapper.findWorkstation(id));
    }

    @Override
    public List<ServiceItemSummary> services(Long storeId, String keyword) {
        return mapper.findServices(storeId, keyword);
    }

    @Override
    public Optional<ServiceItemDetail> findService(long id) {
        ServiceItemRow row = mapper.findService(id);
        return row == null ? Optional.empty() : Optional.of(detail(row));
    }

    @Override
    public Optional<ServiceItemDetail> findServiceByCode(String code) {
        ServiceItemRow row = mapper.findServiceByCode(code);
        return row == null ? Optional.empty() : Optional.of(detail(row));
    }

    @Override
    public CreatedResource createEmployee(NewEmployee employee) {
        String ciphertext = employee.mobile() == null ? null : codec.encrypt(employee.mobile());
        String hash = employee.mobile() == null ? null : codec.searchableHash(employee.mobile());
        String last4 = employee.mobile() == null ? null
                : employee.mobile().substring(employee.mobile().length() - 4);
        long id = mapper.insertEmployee(new ProtectedEmployeeRow(
                employee.employeeNo(), employee.name(), ciphertext, hash, last4,
                employee.positionId(), employee.primaryStoreId(), employee.hireDate(),
                employee.canService(), employee.canSell(),
                employee.createdBy()));
        return new CreatedResource(id);
    }

    @Override
    @Transactional
    public EmployeeSummary updateEmployee(EmployeeUpdate update) {
        boolean mobileChanged = update.mobile() != null;
        String ciphertext = mobileChanged ? codec.encrypt(update.mobile()) : null;
        String hash = mobileChanged ? codec.searchableHash(update.mobile()) : null;
        String last4 = mobileChanged ? update.mobile().substring(update.mobile().length() - 4) : null;
        int updated = mapper.updateEmployee(new ProtectedEmployeeUpdate(
                update.id(), update.name(), mobileChanged, ciphertext, hash, last4,
                update.positionId(), update.primaryStoreId(), update.hireDate(), update.leaveDate(),
                update.canService(), update.canSell(), update.status(), update.version(), update.updatedBy()));
        if (updated == 0) throw stale("员工");
        EmployeeSummary saved = mapper.findEmployee(update.id());
        if (saved == null) throw new IllegalStateException("员工更新后不存在");
        return saved;
    }

    @Override
    public CreatedResource createWorkstation(NewWorkstation workstation) {
        return new CreatedResource(mapper.insertWorkstation(workstation));
    }

    @Override
    @Transactional
    public WorkstationSummary updateWorkstation(WorkstationUpdate update) {
        if (mapper.updateWorkstation(update) == 0) throw stale("工位");
        WorkstationSummary saved = mapper.findWorkstation(update.id());
        if (saved == null) throw new IllegalStateException("工位更新后不存在");
        return saved;
    }

    @Override
    @Transactional
    public CreatedResource createService(NewServiceItem service) {
        long id = mapper.insertService(service);
        service.storeIds().forEach(storeId -> mapper.insertServiceStore(
                id, storeId, service.storePrice(), service.createdBy()));
        return new CreatedResource(id);
    }

    @Override
    @Transactional
    public ServiceItemDetail updateService(ServiceItemUpdate update) {
        if (mapper.updateService(update) == 0) {
            throw new com.yuezhijian.server.common.DuplicateResourceException(
                    "服务项目已被他人修改，请刷新后重试");
        }
        if (mapper.updateServiceStore(update) == 0) {
            throw new IllegalArgumentException("服务项目未配置到所选门店");
        }
        ServiceItemRow row = mapper.findService(update.id());
        if (row == null) throw new IllegalStateException("服务项目更新后不存在");
        return detail(row);
    }

    private ServiceItemDetail detail(ServiceItemRow row) {
        return new ServiceItemDetail(
                row.id(), row.code(), row.name(), row.categoryId(), row.categoryName(), row.durationMinutes(),
                row.costAmount(), row.listPrice(), row.description(), row.status(),
                mapper.findServiceStores(row.id()), row.version());
    }

    private com.yuezhijian.server.common.DuplicateResourceException stale(String resource) {
        return new com.yuezhijian.server.common.DuplicateResourceException(
                resource + "资料已被他人修改，请刷新后重试");
    }
}
