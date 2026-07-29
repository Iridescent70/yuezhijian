package com.yuezhijian.server.asset;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PointAdjustmentRequest(
        @Min(-1000000) @Max(1000000) int changePoints,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 128) String idempotencyKey) {}
