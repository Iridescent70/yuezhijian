package com.yuezhijian.server.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmInventoryRequest(
        @NotBlank String version,
        @Size(max = 500) String reason) {
}
