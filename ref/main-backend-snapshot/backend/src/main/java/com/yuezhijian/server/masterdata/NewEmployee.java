package com.yuezhijian.server.masterdata;

import java.time.LocalDate;

public record NewEmployee(
        String employeeNo,
        String name,
        String mobile,
        long positionId,
        long primaryStoreId,
        LocalDate hireDate,
        boolean canService,
        boolean canSell,
        long createdBy) {
}
