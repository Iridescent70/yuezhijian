package com.yuezhijian.server.trade;

import java.util.List;

public record ReversalDetail(
        ReversalSummary reversal,
        List<ReversalPaymentImpact> payments,
        List<ReversalAssetImpact> assets) {
    public ReversalDetail {
        payments = List.copyOf(payments);
        assets = List.copyOf(assets);
    }
}
