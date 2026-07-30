package com.yuezhijian.server.asset;

public record PointAdjustmentCommand(
        long memberId,
        int changePoints,
        String reason,
        String correlationId,
        long operatorId) {}
