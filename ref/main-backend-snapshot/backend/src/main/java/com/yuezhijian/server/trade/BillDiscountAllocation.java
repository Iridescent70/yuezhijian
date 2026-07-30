package com.yuezhijian.server.trade;

import java.math.BigDecimal;

public record BillDiscountAllocation(
        long billLineId,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal receivableAmount) {}
