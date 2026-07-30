package com.yuezhijian.server.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubmitCardRefundRequest(
        @NotBlank @Size(max = 32) String quoteNo,
        @Positive Long refundMethodId,
        @Positive long storeId,
        @Positive Long employeeId,
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 128) String idempotencyKey) {}
