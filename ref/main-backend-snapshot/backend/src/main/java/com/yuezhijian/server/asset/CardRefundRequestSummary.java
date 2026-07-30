package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardRefundRequestSummary(
        long id,
        long quoteId,
        String requestNo,
        long memberCardId,
        String cardNo,
        String cardTypeName,
        long memberId,
        String memberName,
        String storeName,
        BigDecimal originalAmount,
        BigDecimal consumedRepriceAmount,
        BigDecimal feeAmount,
        BigDecimal refundAmount,
        Long refundMethodId,
        String refundMethodName,
        boolean refundMethodRequiresReference,
        String status,
        String commissionAdjustmentStatus,
        String reason,
        LocalDateTime requestedAt,
        Long requestedBy,
        LocalDateTime reviewedAt,
        Long reviewedBy,
        String reviewComment,
        LocalDateTime executedAt,
        String cardVersion,
        String version) {}
