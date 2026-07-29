package com.yuezhijian.server.masterdata;

public record ProtectedEmployeeRow(
        String employeeNo,
        String name,
        String mobileCiphertext,
        String mobileHash,
        String mobileLast4,
        long positionId,
        long primaryStoreId,
        boolean canService,
        boolean canSell,
        long createdBy) {
}
