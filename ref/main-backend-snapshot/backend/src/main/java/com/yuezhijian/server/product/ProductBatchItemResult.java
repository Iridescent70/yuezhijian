package com.yuezhijian.server.product;

public record ProductBatchItemResult(
        long productId,
        String productCode,
        String productName,
        String status,
        String message) {
}
