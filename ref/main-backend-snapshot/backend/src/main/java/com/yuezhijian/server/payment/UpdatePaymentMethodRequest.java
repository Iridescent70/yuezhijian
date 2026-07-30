package com.yuezhijian.server.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePaymentMethodRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank String type,
        boolean electronic,
        boolean includedInRevenue,
        boolean needsExternalReference,
        @NotBlank String status,
        @NotBlank String version) {
}
