package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BalanceLedgerItem(
        long id,
        long memberId,
        String ledgerNo,
        String transactionType,
        BigDecimal beforeBalance,
        BigDecimal changeAmount,
        BigDecimal afterBalance,
        String sourceType,
        long sourceId,
        long storeId,
        String storeName,
        LocalDateTime occurredAt,
        String correlationId,
        Long reversedLedgerId,
        String note) {}
