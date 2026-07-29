package com.yuezhijian.server.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdatePaymentMethodStoreRequest(
        boolean applicable,
        boolean enabled,
        @Min(0) @Max(9999) int sortNo,
        String version) {
}
