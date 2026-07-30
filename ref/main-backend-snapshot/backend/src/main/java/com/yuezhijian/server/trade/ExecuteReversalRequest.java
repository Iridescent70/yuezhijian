package com.yuezhijian.server.trade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExecuteReversalRequest(
        @NotBlank String version,
        @NotBlank @Size(max = 128) String idempotencyKey) {}
