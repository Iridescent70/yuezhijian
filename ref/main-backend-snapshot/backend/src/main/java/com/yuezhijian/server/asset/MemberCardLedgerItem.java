package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MemberCardLedgerItem(
        long id,
        String ledgerNo,
        long serviceId,
        String serviceName,
        String transactionType,
        BigDecimal beforeTimes,
        BigDecimal changeTimes,
        BigDecimal afterTimes,
        BigDecimal valueAmount,
        String sourceType,
        long sourceId,
        LocalDateTime occurredAt,
        String correlationId,
        Long reversedLedgerId,
        String note) {}
