package com.yuezhijian.server.benefit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BindVoucherCodeRequest(
        @NotNull Long memberId,
        @NotBlank String idempotencyKey) {}
