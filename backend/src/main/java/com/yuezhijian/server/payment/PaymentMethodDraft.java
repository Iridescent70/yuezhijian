package com.yuezhijian.server.payment;

import java.util.List;

public record PaymentMethodDraft(
        String code,
        String name,
        String type,
        boolean electronic,
        boolean includedInRevenue,
        boolean needsExternalReference,
        String status,
        List<Long> storeIds) {
    public PaymentMethodDraft {
        storeIds = List.copyOf(storeIds);
    }
}
