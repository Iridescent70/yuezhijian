package com.yuezhijian.server.trade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SettleBillRequest(
        @NotBlank String quoteNo,
        @NotBlank @Size(max = 128) String idempotencyKey) {
}
