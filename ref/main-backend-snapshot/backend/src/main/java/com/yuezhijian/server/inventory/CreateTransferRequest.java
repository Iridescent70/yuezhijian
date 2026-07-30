package com.yuezhijian.server.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CreateTransferRequest(
        @Positive long sourceStoreId,
        @Positive long targetStoreId,
        @NotNull LocalDate transferDate,
        @Size(max = 500) String remarks,
        @NotEmpty @Size(max = 100) List<@Valid TransferLineRequest> lines,
        @NotNull @Size(min = 8, max = 128) String idempotencyKey) {
}
