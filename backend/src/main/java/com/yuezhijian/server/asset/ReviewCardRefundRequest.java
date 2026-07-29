package com.yuezhijian.server.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewCardRefundRequest(
        boolean approved,
        @Size(max = 1000) String comment,
        @NotBlank @Size(max = 128) String version) {}
