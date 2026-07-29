package com.yuezhijian.server.payment;

public record PaymentMethodUpdate(
        long id,
        String name,
        String type,
        boolean electronic,
        boolean includedInRevenue,
        boolean needsExternalReference,
        String status,
        String version) {
}
