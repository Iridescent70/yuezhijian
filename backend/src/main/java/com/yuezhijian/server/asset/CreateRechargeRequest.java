package com.yuezhijian.server.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRechargeRequest(
        @NotBlank String quoteNo,
        @NotNull Long storeId,
        Long salesEmployeeId,
        @Size(max = 128) String externalReference,
        @NotBlank @Size(max = 128) String idempotencyKey) {}
