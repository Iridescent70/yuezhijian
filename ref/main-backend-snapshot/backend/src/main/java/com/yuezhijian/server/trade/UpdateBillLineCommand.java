package com.yuezhijian.server.trade;

public record UpdateBillLineCommand(
        long billId,
        long lineId,
        BillLineDraft line,
        String version,
        long operatorId) {}
