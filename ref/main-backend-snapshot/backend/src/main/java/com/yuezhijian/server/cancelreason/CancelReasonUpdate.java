package com.yuezhijian.server.cancelreason;

public record CancelReasonUpdate(
        long id,
        String name,
        boolean requiresNote,
        int sortNo,
        String status,
        String version,
        long operatorId) {
}
