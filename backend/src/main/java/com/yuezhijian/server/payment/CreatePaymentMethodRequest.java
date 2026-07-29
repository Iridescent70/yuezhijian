package com.yuezhijian.server.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePaymentMethodRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,63}") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank String type,
        boolean electronic,
        boolean includedInRevenue,
        boolean needsExternalReference,
        @NotBlank String status,
        @NotEmpty List<@NotNull @Positive Long> storeIds) {
}
