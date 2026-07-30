package com.yuezhijian.server.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockLedgerItem(
        long id,
        String ledgerNo,
        long storeId,
        String storeName,
        long giftId,
        String giftCode,
        String giftName,
        String transactionType,
        BigDecimal beforeQuantity,
        BigDecimal changeQuantity,
        BigDecimal afterQuantity,
        String sourceType,
        long sourceId,
        Long sourceLineId,
        LocalDateTime occurredAt,
        Long reversedLedgerId,
        String note,
        String operatorName) {
}
