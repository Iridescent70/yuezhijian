package com.yuezhijian.server.payment;

public record PaymentMethodStoreUpdate(
        long paymentMethodId,
        long storeId,
        boolean applicable,
        boolean enabled,
        int sortNo,
        String version) {
}
