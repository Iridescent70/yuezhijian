package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

record CardTransferRow(
        long id,
        String transferNo,
        long sourceCardId,
        long targetCardId,
        long sourceMemberId,
        long recipientMemberId,
        String recipientMemberName,
        BigDecimal remainingTimes,
        BigDecimal remainingValue,
        LocalDateTime oldExpiresAt,
        LocalDateTime newExpiresAt,
        String reason,
        LocalDateTime executedAt) {}
