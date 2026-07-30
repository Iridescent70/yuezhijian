package com.yuezhijian.server.payment;

import com.yuezhijian.server.trade.PaymentMethodOption;
import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository {
    List<PaymentMethodOption> options(long storeId);

    List<PaymentMethodConfiguration> configurations(
            String keyword, String type, String status, Long storeId);

    Optional<PaymentMethodConfiguration> find(long id, Long storeId);

    boolean existsCode(String code);

    PaymentMethodConfiguration create(PaymentMethodDraft draft);

    PaymentMethodConfiguration update(PaymentMethodUpdate update);

    PaymentMethodConfiguration configureStore(PaymentMethodStoreUpdate update);

    List<PaymentMethodConfiguration> reorder(long storeId, List<PaymentMethodSortUpdate> updates);
}
