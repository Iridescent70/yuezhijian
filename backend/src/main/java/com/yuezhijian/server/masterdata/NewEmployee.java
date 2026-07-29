package com.yuezhijian.server.masterdata;

public record NewEmployee(
        String employeeNo,
        String name,
        String mobile,
        long positionId,
        long primaryStoreId,
        boolean canService,
        boolean canSell,
        long createdBy) {
}
