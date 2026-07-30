package com.yuezhijian.server.trade;

public record AddBillLineCommand(
        long billId,
        BillLineDraft line,
        String version,
        long operatorId) {
}
