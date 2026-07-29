package com.yuezhijian.server.cancelreason;

public record NewCancelReason(
        String businessType,
        String code,
        String name,
        boolean requiresNote,
        int sortNo,
        long operatorId) {
}
