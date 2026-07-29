package com.yuezhijian.server.masterdata;

import com.yuezhijian.server.iam.AccessCatalogService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MasterDataService {
    private final MasterDataRepository repository;
    private final AccessCatalogService accessCatalog;

    public MasterDataService(MasterDataRepository repository, AccessCatalogService accessCatalog) {
        this.repository = repository;
        this.accessCatalog = accessCatalog;
    }

    public List<PositionOption> positions() {
        return repository.positions();
    }

    public List<CategoryOption> serviceCategories() {
        return repository.serviceCategories();
    }

    public List<EmployeeSummary> employees(Long storeId, String keyword) {
        return repository.employees(storeId, blankToNull(keyword));
    }

    public List<WorkstationSummary> workstations(Long storeId) {
        return repository.workstations(storeId);
    }

    public List<ServiceItemSummary> services(Long storeId, String keyword) {
        return repository.services(storeId, blankToNull(keyword));
    }

    public CreatedResource createEmployee(CreateEmployeeRequest request, String username) {
        validateStore(request.primaryStoreId());
        boolean positionExists = repository.positions().stream()
                .anyMatch(position -> position.id() == request.positionId() && "ACTIVE".equals(position.status()));
        if (!positionExists) {
            throw new IllegalArgumentException("所选职务不存在或已停用");
        }
        return repository.createEmployee(new NewEmployee(
                request.employeeNo().trim().toUpperCase(Locale.ROOT),
                request.name().trim(),
                normalizeOptionalMobile(request.mobile()),
                request.positionId(),
                request.primaryStoreId(),
                request.canService(),
                request.canSell(),
                currentUserId(username)));
    }

    public CreatedResource createWorkstation(CreateWorkstationRequest request, String username) {
        validateStore(request.storeId());
        return repository.createWorkstation(new NewWorkstation(
                request.storeId(),
                request.code().trim().toUpperCase(Locale.ROOT),
                request.name().trim(),
                request.capacity(),
                request.sortNo(),
                currentUserId(username)));
    }

    public CreatedResource createService(CreateServiceItemRequest request, String username) {
        boolean categoryExists = repository.serviceCategories().stream()
                .anyMatch(category -> category.id() == request.categoryId() && "ACTIVE".equals(category.status()));
        if (!categoryExists) {
            throw new IllegalArgumentException("所选服务分类不存在或已停用");
        }
        List<Long> storeIds = new LinkedHashSet<>(request.storeIds()).stream().toList();
        storeIds.forEach(this::validateStore);
        if (request.costAmount().compareTo(request.listPrice()) > 0) {
            throw new IllegalArgumentException("服务成本不能高于标准售价");
        }
        return repository.createService(new NewServiceItem(
                request.code().trim().toUpperCase(Locale.ROOT),
                request.name().trim(),
                request.categoryId(),
                request.durationMinutes(),
                request.costAmount(),
                request.listPrice(),
                request.storePrice(),
                storeIds,
                blankToNull(request.description()),
                currentUserId(username)));
    }

    private long currentUserId(String username) {
        return accessCatalog.userIdentity(username).id();
    }

    private void validateStore(long storeId) {
        boolean valid = accessCatalog.stores().stream()
                .anyMatch(store -> store.id() == storeId && "ACTIVE".equals(store.status()));
        if (!valid) {
            throw new IllegalArgumentException("所选门店不存在或已停用");
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
}
