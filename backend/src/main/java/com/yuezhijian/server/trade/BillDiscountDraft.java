package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.util.List;

public record BillDiscountDraft(
        String batchNo,
        long billId,
        String discountType,
        BigDecimal discountValue,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        String reason,
        String version,
        List<BillDiscountAllocation> allocations,
        long operatorId) {
    public BillDiscountDraft {
        allocations = List.copyOf(allocations);
    }
}
