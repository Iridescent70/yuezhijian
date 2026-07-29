package com.yuezhijian.server.trade;

public record PaymentMethodOption(
        long id,
        String code,
        String name,
        String type,
        boolean electronic,
        boolean includedInRevenue,
        boolean needsExternalReference,
        int sortNo) {
}
