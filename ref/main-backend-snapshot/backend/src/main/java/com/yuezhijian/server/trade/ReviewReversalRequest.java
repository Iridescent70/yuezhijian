package com.yuezhijian.server.trade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewReversalRequest(
        boolean approved,
        @Size(max = 1000) String comment,
        @NotBlank String version) {}
