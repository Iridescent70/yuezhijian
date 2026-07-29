package com.yuezhijian.server.payment;

import java.time.LocalDateTime;
import java.util.List;

public record PaymentMethodConfiguration(
        long id,
        String code,
        String name,
        String type,
        boolean electronic,
        boolean includedInRevenue,
        boolean needsExternalReference,
        String status,
        LocalDateTime updatedAt,
        String version,
        List<PaymentMethodStoreConfiguration> stores) {
    public PaymentMethodConfiguration {
        stores = List.copyOf(stores);
    }
}
