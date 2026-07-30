package com.yuezhijian.server.payment;

public record PaymentMethodStoreConfiguration(
        long storeId,
        String storeCode,
        String storeName,
        boolean applicable,
        boolean enabled,
        int sortNo,
        String version) {
}
