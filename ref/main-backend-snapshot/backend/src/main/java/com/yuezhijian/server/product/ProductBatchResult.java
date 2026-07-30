package com.yuezhijian.server.product;

import java.util.List;

public record ProductBatchResult(
        String operation,
        int total,
        int succeeded,
        int skipped,
        int failed,
        List<ProductBatchItemResult> items) {
    public ProductBatchResult {
        items = List.copyOf(items);
    }

    public static ProductBatchResult of(String operation, List<ProductBatchItemResult> items) {
        int succeeded = (int) items.stream().filter(item -> "SUCCESS".equals(item.status())).count();
        int skipped = (int) items.stream().filter(item -> "SKIPPED".equals(item.status())).count();
        return new ProductBatchResult(
                operation, items.size(), succeeded, skipped, items.size() - succeeded - skipped, items);
    }
}
