package com.yuezhijian.server.trade;

public record RemoveBillLineCommand(
        long billId,
        long lineId,
        String version,
        long operatorId) {}
