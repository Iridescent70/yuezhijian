package com.yuezhijian.server.trade;

import java.math.BigDecimal;

public record BillLine(
        long id,
        int lineNo,
        String itemType,
        long itemId,
        String itemCode,
        String itemName,
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal receivableAmount,
        BigDecimal actualAmount,
        Long employeeId,
        String employeeName,
        String note) {
}
