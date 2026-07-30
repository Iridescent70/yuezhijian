package com.yuezhijian.server.inventory;

import java.math.BigDecimal;

public record CountLine(
        long id,
        long giftId,
        String giftCode,
        String giftName,
        String unitName,
        int unitDecimalPlaces,
        BigDecimal bookQuantity,
        BigDecimal actualQuantity,
        BigDecimal differenceQuantity,
        Long stockLedgerId) {
}
