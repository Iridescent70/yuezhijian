package com.yuezhijian.server.audit;

public record OperationChange(
        String field,
        String label,
        String beforeValue,
        String afterValue) {
}
