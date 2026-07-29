package com.yuezhijian.server.asset;

public record PointRefundCommand(
        long reversalId,
        long usageId,
        long memberId,
        int points,
        Long originalLedgerId,
        String note,
        long operatorId) {}
