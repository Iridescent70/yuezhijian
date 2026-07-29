package com.yuezhijian.server.benefit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record IssueVoucherCodesRequest(
        long voucherId,
        @Min(1) @Max(100) int count,
        Long memberId,
        @NotBlank String idempotencyKey) {}
