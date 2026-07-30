package com.yuezhijian.server.inventory;

import java.math.BigDecimal;

public record StockItem(
        long storeId,
        String storeName,
        long giftId,
        String giftCode,
        String giftName,
        String unitName,
        int unitDecimalPlaces,
        BigDecimal onHandQuantity,
        BigDecimal lowStockThreshold,
        boolean lowStock,
        String giftStatus,
        String version) {
}
