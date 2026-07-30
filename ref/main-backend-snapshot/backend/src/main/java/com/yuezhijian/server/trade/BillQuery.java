package com.yuezhijian.server.trade;

import java.time.LocalDate;

public record BillQuery(long storeId, LocalDate startDate, LocalDate endDate, String status, String keyword) {
}
