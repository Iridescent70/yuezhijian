package com.yuezhijian.server.trade;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBillRequest(
        Long memberId,
        @Size(max = 100) String guestName,
        @Size(max = 32) String guestMobile,
        @NotNull Long storeId,
        @Size(max = 32) String sourceType,
        @Min(1) @Max(100) int personCount,
        @Size(max = 1000) String note,
        @Size(max = 128) String idempotencyKey) {
}
