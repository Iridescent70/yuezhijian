package com.yuezhijian.server.masterdata;

import java.time.LocalDate;

public record EmployeeUpdate(
        long id,
        String name,
        String mobile,
        long positionId,
        long primaryStoreId,
        LocalDate hireDate,
        LocalDate leaveDate,
        boolean canService,
        boolean canSell,
        String status,
        String version,
        long updatedBy) {
}
