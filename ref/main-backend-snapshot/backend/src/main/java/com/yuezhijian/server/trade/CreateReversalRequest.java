package com.yuezhijian.server.trade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReversalRequest(
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 128) String idempotencyKey) {}
