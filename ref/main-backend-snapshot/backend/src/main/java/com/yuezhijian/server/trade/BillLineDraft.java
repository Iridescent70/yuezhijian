package com.yuezhijian.server.trade;

import java.math.BigDecimal;

public record BillLineDraft(
        String itemType,
        long itemId,
        String itemCode,
        String itemName,
        BigDecimal unitPrice,
        BigDecimal quantity,
        Long employeeId,
        String employeeName,
        String note) {
    public BigDecimal amount() {
        return unitPrice.multiply(quantity);
    }
}
