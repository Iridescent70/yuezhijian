package com.yuezhijian.server.trade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidBillRequest(
        @NotBlank @Size(max = 64) String reasonCode,
        @Size(max = 500) String note,
        @NotBlank String version) {
}
