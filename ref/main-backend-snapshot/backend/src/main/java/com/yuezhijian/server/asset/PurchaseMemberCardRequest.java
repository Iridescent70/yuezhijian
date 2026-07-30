package com.yuezhijian.server.asset;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PurchaseMemberCardRequest(
        @NotNull Long cardTypeId,
        @Min(1) @Max(20) int quantity,
        @NotNull Long storeId,
        Long salesEmployeeId,
        @NotNull Long paymentMethodId,
        @Size(max = 128) String externalReference,
        LocalDate startDate,
        @NotBlank @Size(max = 128) String idempotencyKey) {}
