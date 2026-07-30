package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReversalSummary(
        long id,
        String reversalNo,
        long billId,
        String billNo,
        String customerName,
        String storeName,
        BigDecimal refundAmount,
        String status,
        String reason,
        LocalDateTime requestedAt,
        Long requestedBy,
        LocalDateTime reviewedAt,
        Long reviewedBy,
        String reviewComment,
        LocalDateTime executedAt,
        String version) {}
