package com.yuezhijian.server.inventory;

import java.math.BigDecimal;

public record StockLockRow(long id, long storeId, long giftId, BigDecimal onHandQuantity, byte[] rowVersion) {
}
