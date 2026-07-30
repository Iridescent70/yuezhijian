package com.yuezhijian.server.inventory;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CreateCountRequest(
        @Positive long storeId,
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalDate countDate,
        @NotEmpty @Size(max = 500) List<@Positive Long> giftIds,
        @Size(max = 500) String remarks,
        @NotNull @Size(min = 8, max = 128) String idempotencyKey) {
}
