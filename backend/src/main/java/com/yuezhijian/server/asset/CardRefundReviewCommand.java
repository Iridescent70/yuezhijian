package com.yuezhijian.server.asset;

public record CardRefundReviewCommand(
        CardRefundRequestDetail request,
        boolean approved,
        String comment,
        String version,
        long operatorId) {}
