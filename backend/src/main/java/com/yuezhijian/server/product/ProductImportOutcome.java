package com.yuezhijian.server.product;

public record ProductImportOutcome(long productId, boolean created, String message) {
}
