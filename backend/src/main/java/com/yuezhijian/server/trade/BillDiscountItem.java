package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillDiscountItem(
        long id,
        String batchNo,
        long billLineId,
        String discountType,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        String reason,
        long authorizationUserId,
        LocalDateTime createdAt) {}
