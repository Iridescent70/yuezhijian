package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardTransferCommand(
        String transferNo,
        MemberCardSummary sourceCard,
        long recipientMemberId,
        String recipientMemberName,
        BigDecimal remainingTimes,
        BigDecimal remainingValue,
        LocalDateTime newExpiresAt,
        long storeId,
        Long employeeId,
        String reason,
        LocalDateTime executedAt,
        String idempotencyKey,
        long operatorId) {}
