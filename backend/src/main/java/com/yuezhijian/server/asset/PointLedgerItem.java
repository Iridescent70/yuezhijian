package com.yuezhijian.server.asset;

import java.time.LocalDateTime;

public record PointLedgerItem(
        long id,
        long memberId,
        String ledgerNo,
        String transactionType,
        int beforePoints,
        int changePoints,
        int afterPoints,
        String sourceType,
        long sourceId,
        LocalDateTime expiredAt,
        LocalDateTime occurredAt,
        String correlationId,
        Long reversedLedgerId,
        String note) {}
