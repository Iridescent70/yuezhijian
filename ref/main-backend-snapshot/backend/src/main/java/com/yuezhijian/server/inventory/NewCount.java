package com.yuezhijian.server.inventory;

import java.time.LocalDate;
import java.util.List;

public record NewCount(
        String countNo,
        String name,
        long storeId,
        LocalDate countDate,
        List<Long> giftIds,
        String remarks,
        String idempotencyKey,
        long operatorId) {
}
