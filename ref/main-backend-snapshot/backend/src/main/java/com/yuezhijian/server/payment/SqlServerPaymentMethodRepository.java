package com.yuezhijian.server.payment;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.trade.PaymentMethodOption;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerPaymentMethodRepository implements PaymentMethodRepository {
    private final PaymentMethodMapper mapper;

    public SqlServerPaymentMethodRepository(PaymentMethodMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<PaymentMethodOption> options(long storeId) {
        return mapper.findOptions(storeId);
    }

    @Override
    public List<PaymentMethodConfiguration> configurations(
            String keyword, String type, String status, Long storeId) {
        return mapper.findMethods(keyword, type, status).stream()
                .map(row -> configuration(row, storeId))
                .sorted(storeId == null
                        ? java.util.Comparator.comparing(PaymentMethodConfiguration::code)
                        : java.util.Comparator
                                .comparingInt((PaymentMethodConfiguration item) ->
                                        item.stores().getFirst().applicable() ? 0 : 1)
                                .thenComparingInt(item -> item.stores().getFirst().sortNo())
                                .thenComparingLong(PaymentMethodConfiguration::id))
                .toList();
    }

    @Override
    public Optional<PaymentMethodConfiguration> find(long id, Long storeId) {
        PaymentMethodRow row = mapper.findMethod(id);
        return row == null ? Optional.empty() : Optional.of(configuration(row, storeId));
    }

    @Override
    public boolean existsCode(String code) {
        return mapper.countCode(code) > 0;
    }

    @Override
    public PaymentMethodConfiguration create(PaymentMethodDraft draft) {
        try {
            long id = mapper.insertMethod(draft);
            for (Long storeId : draft.storeIds()) {
                mapper.insertStore(id, storeId, mapper.nextSortNo(storeId), true);
            }
            return find(id, null).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("支付方式编号或门店配置已存在");
        }
    }

    @Override
    public PaymentMethodConfiguration update(PaymentMethodUpdate update) {
        if (mapper.updateMethod(update) == 0) {
            if (mapper.findMethod(update.id()) == null) throw new ResourceNotFoundException("支付方式不存在");
            throw new DuplicateResourceException("支付方式已被他人修改，请刷新后重试");
        }
        return find(update.id(), null).orElseThrow();
    }

    @Override
    public PaymentMethodConfiguration configureStore(PaymentMethodStoreUpdate update) {
        if (mapper.findMethod(update.paymentMethodId()) == null) {
            throw new ResourceNotFoundException("支付方式不存在");
        }
        PaymentMethodStoreConfiguration current =
                mapper.findConfiguredStore(update.paymentMethodId(), update.storeId());
        try {
            if (!update.applicable()) {
                if (current != null && mapper.deleteStore(update) == 0) conflict();
            } else if (current == null) {
                if (update.version() != null && !update.version().isBlank()) conflict();
                mapper.insertStore(
                        update.paymentMethodId(), update.storeId(), update.sortNo(), update.enabled());
            } else if (mapper.updateStore(update) == 0) {
                conflict();
            }
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("门店支付配置已变化，请刷新后重试");
        }
        return find(update.paymentMethodId(), update.storeId()).orElseThrow();
    }

    @Override
    public List<PaymentMethodConfiguration> reorder(
            long storeId, List<PaymentMethodSortUpdate> updates) {
        for (PaymentMethodSortUpdate update : updates) {
            if (mapper.updateSort(storeId, update) == 0) conflict();
        }
        return configurations(null, null, null, storeId);
    }

    private PaymentMethodConfiguration configuration(PaymentMethodRow row, Long storeId) {
        return new PaymentMethodConfiguration(
                row.id(), row.code(), row.name(), row.type(), row.electronic(), row.includedInRevenue(),
                row.needsExternalReference(), row.status(), row.updatedAt(), row.version(),
                mapper.findStores(row.id(), storeId));
    }

    private static void conflict() {
        throw new DuplicateResourceException("门店支付配置已被他人修改，请刷新后重试");
    }
}
