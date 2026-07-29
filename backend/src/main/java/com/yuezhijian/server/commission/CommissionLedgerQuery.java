package com.yuezhijian.server.commission;

import java.time.LocalDate;

public record CommissionLedgerQuery(
        Long employeeId,
        Long storeId,
        LocalDate startDate,
        LocalDate endDate,
        String direction,
        String calculationStatus) {
}
