package com.yuezhijian.server.trade;

import java.util.List;

public record BillDetail(
        BillSummary bill,
        List<BillLine> lines,
        List<BillPayment> payments,
        List<BillDiscountItem> discounts,
        List<BillAssetUsageItem> assetUsages,
        List<BillHistoryItem> history) {
    public BillDetail {
        lines = List.copyOf(lines);
        payments = List.copyOf(payments);
        discounts = List.copyOf(discounts);
        assetUsages = List.copyOf(assetUsages);
        history = List.copyOf(history);
    }
}
