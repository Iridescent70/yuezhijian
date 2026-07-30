package com.yuezhijian.server.payment;

import java.time.LocalDateTime;

public record PaymentMethodRow(
        long id,
        String code,
        String name,
        String type,
        boolean electronic,
        boolean includedInRevenue,
        boolean needsExternalReference,
        String status,
        LocalDateTime updatedAt,
        String version) {
}
