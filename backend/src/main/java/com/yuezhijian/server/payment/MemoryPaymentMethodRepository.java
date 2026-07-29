package com.yuezhijian.server.payment;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreSummary;
import com.yuezhijian.server.trade.PaymentMethodOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryPaymentMethodRepository implements PaymentMethodRepository {
    private final Map<Long, PaymentMethodRow> methods = new LinkedHashMap<>();
    private final Map<String, StoreState> storeStates = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong(5);
    private final AccessCatalogService accessCatalog;

    public MemoryPaymentMethodRepository(AccessCatalogService accessCatalog) {
        this.accessCatalog = accessCatalog;
        seed(1, "CASH", "现金", "CASH", false, true, false, 10);
        seed(2, "BANK_CARD", "银行卡", "BANK_CARD", true, true, false, 20);
        seed(3, "WECHAT", "微信支付", "WECHAT", true, true, true, 30);
        seed(4, "ALIPAY", "支付宝", "ALIPAY", true, true, true, 40);
        seed(5, "MEITUAN", "美团核销", "MEITUAN", true, true, true, 50);
    }

    @Override
    public synchronized List<PaymentMethodOption> options(long storeId) {
        return methods.values().stream()
                .filter(method -> "ACTIVE".equals(method.status()))
                .filter(method -> {
                    StoreState state = storeStates.get(key(method.id(), storeId));
                    return state != null && state.enabled();
                })
                .map(method -> {
                    StoreState state = storeStates.get(key(method.id(), storeId));
                    return new PaymentMethodOption(
                            method.id(), method.code(), method.name(), method.type(), method.electronic(),
                            method.includedInRevenue(), method.needsExternalReference(), state.sortNo());
                })
                .sorted(Comparator.comparingInt(PaymentMethodOption::sortNo)
                        .thenComparingLong(PaymentMethodOption::id))
                .toList();
    }

    @Override
    public synchronized List<PaymentMethodConfiguration> configurations(
            String keyword, String type, String status, Long storeId) {
        String normalizedKeyword = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        Comparator<PaymentMethodConfiguration> comparator = storeId == null
                ? Comparator.comparing(PaymentMethodConfiguration::code)
                : Comparator.comparingInt((PaymentMethodConfiguration item) -> item.stores().getFirst().applicable() ? 0 : 1)
                        .thenComparingInt(item -> item.stores().getFirst().sortNo())
                        .thenComparingLong(PaymentMethodConfiguration::id);
        return methods.values().stream()
                .filter(method -> normalizedKeyword == null
                        || method.code().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || method.name().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .filter(method -> type == null || type.equals(method.type()))
                .filter(method -> status == null || status.equals(method.status()))
                .map(method -> configuration(method, storeId))
                .sorted(comparator)
                .toList();
    }

    @Override
    public synchronized Optional<PaymentMethodConfiguration> find(long id, Long storeId) {
        return Optional.ofNullable(methods.get(id)).map(method -> configuration(method, storeId));
    }

    @Override
    public synchronized boolean existsCode(String code) {
        return methods.values().stream().anyMatch(method -> method.code().equalsIgnoreCase(code));
    }

    @Override
    public synchronized PaymentMethodConfiguration create(PaymentMethodDraft draft) {
        if (existsCode(draft.code())) throw new DuplicateResourceException("支付方式编号已存在");
        long id = ids.incrementAndGet();
        PaymentMethodRow created = new PaymentMethodRow(
                id, draft.code(), draft.name(), draft.type(), draft.electronic(), draft.includedInRevenue(),
                draft.needsExternalReference(), draft.status(), LocalDateTime.now(), "1");
        methods.put(id, created);
        for (Long storeId : draft.storeIds()) {
            storeStates.put(key(id, storeId), new StoreState(id, storeId, true, nextSortNo(storeId), "1"));
        }
        return configuration(created, null);
    }

    @Override
    public synchronized PaymentMethodConfiguration update(PaymentMethodUpdate update) {
        PaymentMethodRow current = methods.get(update.id());
        if (current == null) throw new ResourceNotFoundException("支付方式不存在");
        requireVersion(current.version(), update.version());
        PaymentMethodRow changed = new PaymentMethodRow(
                current.id(), current.code(), update.name(), update.type(), update.electronic(),
                update.includedInRevenue(), update.needsExternalReference(), update.status(),
                LocalDateTime.now(), nextVersion(current.version()));
        methods.put(changed.id(), changed);
        return configuration(changed, null);
    }

    @Override
    public synchronized PaymentMethodConfiguration configureStore(PaymentMethodStoreUpdate update) {
        if (!methods.containsKey(update.paymentMethodId())) throw new ResourceNotFoundException("支付方式不存在");
        String key = key(update.paymentMethodId(), update.storeId());
        StoreState current = storeStates.get(key);
        if (!update.applicable()) {
            if (current != null) {
                requireVersion(current.version(), update.version());
                storeStates.remove(key);
            }
        } else if (current == null) {
            if (update.version() != null && !update.version().isBlank()) {
                throw new DuplicateResourceException("门店支付配置已变化，请刷新后重试");
            }
            storeStates.put(key, new StoreState(
                    update.paymentMethodId(), update.storeId(), update.enabled(), update.sortNo(), "1"));
        } else {
            requireVersion(current.version(), update.version());
            storeStates.put(key, new StoreState(
                    current.paymentMethodId(), current.storeId(), update.enabled(), update.sortNo(),
                    nextVersion(current.version())));
        }
        return configuration(methods.get(update.paymentMethodId()), update.storeId());
    }

    @Override
    public synchronized List<PaymentMethodConfiguration> reorder(
            long storeId, List<PaymentMethodSortUpdate> updates) {
        for (PaymentMethodSortUpdate update : updates) {
            StoreState current = storeStates.get(key(update.paymentMethodId(), storeId));
            if (current == null) throw new IllegalArgumentException("排序项不是当前门店适用的支付方式");
            requireVersion(current.version(), update.version());
        }
        for (PaymentMethodSortUpdate update : updates) {
            String key = key(update.paymentMethodId(), storeId);
            StoreState current = storeStates.get(key);
            storeStates.put(key, new StoreState(
                    current.paymentMethodId(), storeId, current.enabled(), update.sortNo(),
                    nextVersion(current.version())));
        }
        return configurations(null, null, null, storeId);
    }

    private PaymentMethodConfiguration configuration(PaymentMethodRow method, Long requestedStoreId) {
        List<StoreSummary> stores = accessCatalog.stores().stream()
                .filter(store -> requestedStoreId == null || store.id() == requestedStoreId)
                .toList();
        List<PaymentMethodStoreConfiguration> configurations = new ArrayList<>();
        for (StoreSummary store : stores) {
            StoreState state = storeStates.get(key(method.id(), store.id()));
            configurations.add(new PaymentMethodStoreConfiguration(
                    store.id(), store.code(), store.name(), state != null, state != null && state.enabled(),
                    state == null ? 0 : state.sortNo(), state == null ? null : state.version()));
        }
        return new PaymentMethodConfiguration(
                method.id(), method.code(), method.name(), method.type(), method.electronic(),
                method.includedInRevenue(), method.needsExternalReference(), method.status(),
                method.updatedAt(), method.version(), configurations);
    }

    private int nextSortNo(long storeId) {
        return storeStates.values().stream().filter(state -> state.storeId() == storeId)
                .mapToInt(StoreState::sortNo).max().orElse(0) + 10;
    }

    private void seed(
            long id, String code, String name, String type, boolean electronic,
            boolean includedInRevenue, boolean needsExternalReference, int sortNo) {
        methods.put(id, new PaymentMethodRow(
                id, code, name, type, electronic, includedInRevenue, needsExternalReference,
                "ACTIVE", LocalDateTime.now(), "1"));
        for (StoreSummary store : accessCatalog.stores()) {
            storeStates.put(key(id, store.id()), new StoreState(id, store.id(), true, sortNo, "1"));
        }
    }

    private static void requireVersion(String current, String requested) {
        if (requested == null || !current.equalsIgnoreCase(requested)) {
            throw new DuplicateResourceException("支付方式配置已被他人修改，请刷新后重试");
        }
    }

    private static String nextVersion(String version) {
        return String.valueOf(Long.parseLong(version) + 1);
    }

    private static String key(long paymentMethodId, long storeId) {
        return paymentMethodId + ":" + storeId;
    }

    private record StoreState(
            long paymentMethodId, long storeId, boolean enabled, int sortNo, String version) {
    }
}
