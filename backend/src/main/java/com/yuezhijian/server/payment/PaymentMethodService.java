package com.yuezhijian.server.payment;

import com.yuezhijian.server.audit.AuditService;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreDataScope;
import com.yuezhijian.server.iam.StoreSummary;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {
    private static final Set<String> TYPES = Set.of(
            "CASH", "BANK_CARD", "WECHAT", "ALIPAY", "MEITUAN", "STORED_VALUE", "OTHER");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");

    private final PaymentMethodRepository repository;
    private final AccessCatalogService accessCatalog;
    private final StoreDataScope storeDataScope;
    private final AuditService audit;

    public PaymentMethodService(
            PaymentMethodRepository repository,
            AccessCatalogService accessCatalog,
            StoreDataScope storeDataScope,
            AuditService audit) {
        this.repository = repository;
        this.accessCatalog = accessCatalog;
        this.storeDataScope = storeDataScope;
        this.audit = audit;
    }

    public List<PaymentMethodConfiguration> configurations(
            String keyword, String type, String status, Long storeId) {
        Long scopedStoreId = storeDataScope.constrainNullable(storeId);
        return repository.configurations(
                optional(keyword), enumValue(type, TYPES, "支付类型"),
                enumValue(status, STATUSES, "支付方式状态"), scopedStoreId);
    }

    public PaymentMethodConfiguration detail(long id, Long storeId) {
        Long scopedStoreId = storeDataScope.constrainNullable(storeId);
        return repository.find(id, scopedStoreId)
                .orElseThrow(() -> new ResourceNotFoundException("支付方式不存在"));
    }

    @Transactional
    public PaymentMethodConfiguration create(CreatePaymentMethodRequest request, String username) {
        storeDataScope.requireAllStoreAccess();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsCode(code)) throw new IllegalArgumentException("支付方式编号已存在");
        List<Long> storeIds = distinctStores(request.storeIds());
        long operatorId = accessCatalog.userIdentity(username).id();
        PaymentMethodConfiguration created = repository.create(new PaymentMethodDraft(
                code, request.name().trim(), enumValue(request.type(), TYPES, "支付类型"),
                request.electronic(), request.includedInRevenue(), request.needsExternalReference(),
                enumValue(request.status(), STATUSES, "支付方式状态"), storeIds));
        audit.record("PAYMENT", "CREATE", "PAYMENT_METHOD", created.id(), null,
                null, snapshot(created), operatorId);
        return created;
    }

    @Transactional
    public PaymentMethodConfiguration update(
            long id, UpdatePaymentMethodRequest request, String username) {
        storeDataScope.requireAllStoreAccess();
        PaymentMethodConfiguration before = repository.find(id, null)
                .orElseThrow(() -> new ResourceNotFoundException("支付方式不存在"));
        long operatorId = accessCatalog.userIdentity(username).id();
        PaymentMethodConfiguration updated = repository.update(new PaymentMethodUpdate(
                id, request.name().trim(), enumValue(request.type(), TYPES, "支付类型"),
                request.electronic(), request.includedInRevenue(), request.needsExternalReference(),
                enumValue(request.status(), STATUSES, "支付方式状态"), request.version()));
        audit.record("PAYMENT", "UPDATE", "PAYMENT_METHOD", id, null,
                snapshot(before), snapshot(updated), operatorId);
        return updated;
    }

    @Transactional
    public PaymentMethodConfiguration configureStore(
            long id, long storeId, UpdatePaymentMethodStoreRequest request, String username) {
        storeDataScope.require(storeId);
        PaymentMethodConfiguration method = repository.find(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("支付方式不存在"));
        PaymentMethodStoreConfiguration before = method.stores().getFirst();
        int sortNo = request.applicable() && request.sortNo() == 0
                ? nextSortNo(storeId)
                : request.sortNo();
        PaymentMethodConfiguration updated = repository.configureStore(new PaymentMethodStoreUpdate(
                id, storeId, request.applicable(), request.applicable() && request.enabled(),
                sortNo, request.version()));
        long operatorId = accessCatalog.userIdentity(username).id();
        audit.record("PAYMENT", "STORE_CONFIG", "PAYMENT_METHOD", id, storeId,
                storeSnapshot(before), storeSnapshot(updated.stores().getFirst()), operatorId);
        return updated;
    }

    @Transactional
    public List<PaymentMethodConfiguration> reorder(
            SortPaymentMethodsRequest request, String username) {
        storeDataScope.require(request.storeId());
        List<PaymentMethodConfiguration> current =
                repository.configurations(null, null, null, request.storeId());
        Map<Long, PaymentMethodStoreConfiguration> applicable = new LinkedHashMap<>();
        current.forEach(method -> {
            PaymentMethodStoreConfiguration store = method.stores().getFirst();
            if (store.applicable()) applicable.put(method.id(), store);
        });
        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>();
        LinkedHashSet<Integer> requestedSorts = new LinkedHashSet<>();
        for (PaymentMethodSortItemRequest item : request.items()) {
            if (!requestedIds.add(item.paymentMethodId())) throw new IllegalArgumentException("排序项不能重复");
            if (!requestedSorts.add(item.sortNo())) throw new IllegalArgumentException("排序值不能重复");
        }
        if (!requestedIds.equals(applicable.keySet())) {
            throw new IllegalArgumentException("排序必须包含当前门店全部适用支付方式");
        }
        List<PaymentMethodSortUpdate> updates = request.items().stream()
                .map(item -> new PaymentMethodSortUpdate(
                        item.paymentMethodId(), item.sortNo(), item.version()))
                .toList();
        List<PaymentMethodConfiguration> reordered = repository.reorder(request.storeId(), updates);
        long operatorId = accessCatalog.userIdentity(username).id();
        for (PaymentMethodSortItemRequest item : request.items()) {
            PaymentMethodStoreConfiguration before = applicable.get(item.paymentMethodId());
            PaymentMethodStoreConfiguration after = reordered.stream()
                    .filter(method -> method.id() == item.paymentMethodId()).findFirst().orElseThrow()
                    .stores().getFirst();
            if (before.sortNo() != after.sortNo()) {
                audit.record("PAYMENT", "SORT", "PAYMENT_METHOD", item.paymentMethodId(), request.storeId(),
                        storeSnapshot(before), storeSnapshot(after), operatorId);
            }
        }
        return reordered;
    }

    private List<Long> distinctStores(List<Long> requested) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>(requested);
        Set<Long> active = accessCatalog.stores().stream().map(StoreSummary::id).collect(java.util.stream.Collectors.toSet());
        if (unique.size() != requested.size()) throw new IllegalArgumentException("适用门店不能重复");
        if (!active.containsAll(unique)) throw new IllegalArgumentException("适用门店不存在或已停用");
        return List.copyOf(unique);
    }

    private int nextSortNo(long storeId) {
        return repository.configurations(null, null, null, storeId).stream()
                .map(PaymentMethodConfiguration::stores).map(List::getFirst)
                .filter(PaymentMethodStoreConfiguration::applicable)
                .mapToInt(PaymentMethodStoreConfiguration::sortNo).max().orElse(0) + 10;
    }

    private Map<String, Object> snapshot(PaymentMethodConfiguration method) {
        return Map.of(
                "code", method.code(), "name", method.name(), "type", method.type(),
                "electronic", method.electronic(), "includedInRevenue", method.includedInRevenue(),
                "needsExternalReference", method.needsExternalReference(), "status", method.status());
    }

    private Map<String, Object> storeSnapshot(PaymentMethodStoreConfiguration store) {
        return Map.of(
                "storeName", store.storeName(), "applicable", store.applicable(),
                "enabled", store.enabled(), "sortNo", store.sortNo());
    }

    private static String enumValue(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + "无效");
        return normalized;
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 100) throw new IllegalArgumentException("支付方式查询不能超过100个字符");
        return normalized;
    }
}
