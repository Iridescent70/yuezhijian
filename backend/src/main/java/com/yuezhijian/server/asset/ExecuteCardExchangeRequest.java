package com.yuezhijian.server.asset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ExecuteCardExchangeRequest(
        @NotBlank @Size(max = 32) String quoteNo,
        @Positive long storeId,
        @Positive Long employeeId,
        @NotNull List<@Valid CardExchangePaymentRequest> payments,
        @NotBlank @Size(max = 128) String idempotencyKey) {
    public ExecuteCardExchangeRequest {
        payments = payments == null ? List.of() : List.copyOf(payments);
    }
}
