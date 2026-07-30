package com.yuezhijian.server.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InventoryActionRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotBlank String version) {
}
