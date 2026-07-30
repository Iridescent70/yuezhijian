package com.yuezhijian.server.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExecuteCardRefundRequest(
        @NotBlank @Size(max = 128) String version,
        @Size(max = 128) String externalRefundReference,
        @NotBlank @Size(max = 128) String idempotencyKey) {}
