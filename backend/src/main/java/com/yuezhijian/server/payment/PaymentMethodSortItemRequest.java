package com.yuezhijian.server.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PaymentMethodSortItemRequest(
        @Positive long paymentMethodId,
        @Min(0) @Max(9999) int sortNo,
        @NotBlank String version) {
}
