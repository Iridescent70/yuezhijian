package com.yuezhijian.server.servicearea;

import com.yuezhijian.server.audit.AuditService;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreDataScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceAreaService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private final ServiceAreaRepository repository;
    private final AccessCatalogService accessCatalog;
    private final StoreDataScope storeDataScope;
    private final AuditService audit;

    public ServiceAreaService(
            ServiceAreaRepository repository,
            AccessCatalogService accessCatalog,
            StoreDataScope storeDataScope,
            AuditService audit) {
        this.repository = repository;
        this.accessCatalog = accessCatalog;
        this.storeDataScope = storeDataScope;
        this.audit = audit;
    }

    public List<ServiceArea> findAll(Long storeId, String keyword, String status) {
        Long scopedStoreId = storeDataScope.constrainNullable(storeId);
        return repository.findAll(scopedStoreId, optional(keyword, 300), enumValue(status));
    }

    public ServiceArea detail(long id) {
        ServiceArea area = repository.find(id)
                .orElseThrow(() -> new ResourceNotFoundException("服务小区不存在"));
        storeDataScope.require(area.storeId());
        return area;
    }

    @Transactional
    public ServiceArea create(CreateServiceAreaRequest request, String username) {
        storeDataScope.require(request.storeId());
        long operatorId = accessCatalog.userIdentity(username).id();
        ServiceArea created = repository.create(new NewServiceArea(
                request.storeId(), request.city().trim(), request.district().trim(), request.address().trim(),
                coordinate(request.longitude()), coordinate(request.latitude()),
                request.radiusKm().setScale(3, RoundingMode.UNNECESSARY),
                request.visitFee().setScale(4, RoundingMode.UNNECESSARY), operatorId));
        audit.record("HOME_SERVICE", "CREATE", "SERVICE_AREA", created.id(), created.storeId(),
                null, snapshot(created), operatorId);
        return created;
    }

    @Transactional
    public ServiceArea update(long id, UpdateServiceAreaRequest request, String username) {
        ServiceArea before = detail(id);
        long operatorId = accessCatalog.userIdentity(username).id();
        ServiceArea updated = repository.update(new ServiceAreaUpdate(
                id, request.city().trim(), request.district().trim(), request.address().trim(),
                coordinate(request.longitude()), coordinate(request.latitude()),
                request.radiusKm().setScale(3, RoundingMode.UNNECESSARY),
                request.visitFee().setScale(4, RoundingMode.UNNECESSARY),
                enumValue(request.status()), request.version(), operatorId));
        audit.record("HOME_SERVICE", "UPDATE", "SERVICE_AREA", id, before.storeId(),
                snapshot(before), snapshot(updated), operatorId);
        return updated;
    }

    private Map<String, Object> snapshot(ServiceArea area) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("storeName", area.storeName());
        value.put("city", area.city());
        value.put("district", area.district());
        value.put("address", area.address());
        value.put("longitude", area.longitude());
        value.put("latitude", area.latitude());
        value.put("radiusKm", area.radiusKm());
        value.put("visitFee", area.visitFee());
        value.put("status", area.status());
        return value;
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException("服务小区查询不能超过300个字符");
        return normalized;
    }

    private static String enumValue(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("服务小区状态无效");
        return normalized;
    }

    private static BigDecimal coordinate(BigDecimal value) {
        return value.setScale(7, RoundingMode.UNNECESSARY);
    }
}
