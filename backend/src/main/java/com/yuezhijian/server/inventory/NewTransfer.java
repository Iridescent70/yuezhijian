package com.yuezhijian.server.inventory;

import java.time.LocalDate;
import java.util.List;

public record NewTransfer(
        String transferNo,
        long sourceStoreId,
        long targetStoreId,
        LocalDate transferDate,
        String remarks,
        List<TransferLineRequest> lines,
        String idempotencyKey,
        long operatorId) {
}
