package com.yuezhijian.server.masterdata;

import java.time.LocalDate;

public record EmployeeSummary(
        long id,
        String employeeNo,
        String name,
        String maskedMobile,
        Long positionId,
        String positionName,
        Long storeId,
        String storeName,
        LocalDate hireDate,
        LocalDate leaveDate,
        boolean canService,
        boolean canSell,
        String status,
        String version) {
}
