package com.yuezhijian.server.servicearea;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreSummary;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryServiceAreaRepository implements ServiceAreaRepository {
    private final List<ServiceArea> areas = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(2);
    private final AccessCatalogService accessCatalog;

    public MemoryServiceAreaRepository(AccessCatalogService accessCatalog) {
        this.accessCatalog = accessCatalog;
        areas.add(seed(1, 1, "上海市", "静安区", "南京西路示范服务区", "121.4590000", "31.2290000"));
        areas.add(seed(2, 2, "上海市", "浦东新区", "世纪大道示范服务区", "121.5060000", "31.2450000"));
    }

    @Override
    public synchronized List<ServiceArea> findAll(Long storeId, String keyword, String status) {
        String normalizedKeyword = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return areas.stream()
                .filter(area -> storeId == null || area.storeId() == storeId)
                .filter(area -> status == null || status.equals(area.status()))
                .filter(area -> normalizedKeyword == null
                        || area.city().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || area.district().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || area.address().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted(Comparator.comparing(ServiceArea::storeCode)
                        .thenComparingInt(area -> "ACTIVE".equals(area.status()) ? 0 : 1)
                        .thenComparing(ServiceArea::city)
                        .thenComparing(ServiceArea::district)
                        .thenComparingLong(ServiceArea::id))
                .toList();
    }

    @Override
    public synchronized Optional<ServiceArea> find(long id) {
        return areas.stream().filter(area -> area.id() == id).findFirst();
    }

    @Override
    public synchronized ServiceArea create(NewServiceArea draft) {
        requireUnique(draft.storeId(), draft.address(), null);
        StoreSummary store = store(draft.storeId());
        long id = ids.incrementAndGet();
        ServiceArea created = new ServiceArea(
                id, store.id(), store.code(), store.name(), draft.city(), draft.district(), draft.address(),
                draft.longitude(), draft.latitude(), draft.radiusKm(), draft.visitFee(), "ACTIVE",
                LocalDateTime.now(), draft.operatorId(), operatorName(draft.operatorId()), "1");
        areas.add(created);
        return created;
    }

    @Override
    public synchronized ServiceArea update(ServiceAreaUpdate update) {
        ServiceArea current = find(update.id())
                .orElseThrow(() -> new ResourceNotFoundException("服务小区不存在"));
        if (!current.version().equals(update.version())) {
            throw new DuplicateResourceException("服务小区已被他人修改，请刷新后重试");
        }
        requireUnique(current.storeId(), update.address(), current.id());
        ServiceArea saved = new ServiceArea(
                current.id(), current.storeId(), current.storeCode(), current.storeName(),
                update.city(), update.district(), update.address(), update.longitude(), update.latitude(),
                update.radiusKm(), update.visitFee(), update.status(), LocalDateTime.now(),
                update.operatorId(), operatorName(update.operatorId()), nextVersion(current.version()));
        areas.set(areas.indexOf(current), saved);
        return saved;
    }

    private ServiceArea seed(long id, long storeId, String city, String district, String address,
            String longitude, String latitude) {
        StoreSummary store = store(storeId);
        return new ServiceArea(
                id, storeId, store.code(), store.name(), city, district, address,
                new java.math.BigDecimal(longitude), new java.math.BigDecimal(latitude),
                new java.math.BigDecimal("5.000"), new java.math.BigDecimal("30.0000"),
                "ACTIVE", LocalDateTime.now(), 1L, "本地管理员", "1");
    }

    private StoreSummary store(long storeId) {
        return accessCatalog.stores().stream().filter(item -> item.id() == storeId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("所选门店不存在或已停用"));
    }

    private String operatorName(long operatorId) {
        return operatorId == 1L ? "本地管理员" : "用户" + operatorId;
    }

    private void requireUnique(long storeId, String address, Long excludedId) {
        boolean exists = areas.stream().anyMatch(area -> area.storeId() == storeId
                && area.address().equalsIgnoreCase(address)
                && (excludedId == null || area.id() != excludedId));
        if (exists) throw new DuplicateResourceException("该门店已存在相同服务地址");
    }

    private static String nextVersion(String version) {
        return String.valueOf(Long.parseLong(version) + 1);
    }
}
