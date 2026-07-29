package com.yuezhijian.server.masterdata;

public record EmployeeSummary(
        long id,
        String employeeNo,
        String name,
        String maskedMobile,
        Long positionId,
        String positionName,
        Long storeId,
        String storeName,
        boolean canService,
        boolean canSell,
        String status) {
}
