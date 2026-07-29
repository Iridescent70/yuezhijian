package com.yuezhijian.server.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechargeActionRequest(
        @NotBlank String version,
        @Size(max = 500) String reason) {}
