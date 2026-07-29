package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardTransferResult(
        long transferId,
        String transferNo,
        MemberCardSummary sourceCard,
        MemberCardSummary targetCard,
        long sourceMemberId,
        long recipientMemberId,
        String recipientMemberName,
        BigDecimal remainingTimes,
        BigDecimal remainingValue,
        LocalDateTime oldExpiresAt,
        LocalDateTime newExpiresAt,
        String reason,
        LocalDateTime executedAt) {}
