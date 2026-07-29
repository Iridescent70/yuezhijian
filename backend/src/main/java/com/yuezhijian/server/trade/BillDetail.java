package com.yuezhijian.server.trade;

import java.util.List;

public record BillDetail(
        BillSummary bill,
        List<BillLine> lines,
        List<BillPayment> payments,
        List<BillHistoryItem> history) {
    public BillDetail {
        lines = List.copyOf(lines);
        payments = List.copyOf(payments);
        history = List.copyOf(history);
    }
}
