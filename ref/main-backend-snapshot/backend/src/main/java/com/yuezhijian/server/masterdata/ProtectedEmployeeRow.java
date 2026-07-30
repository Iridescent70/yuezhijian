package com.yuezhijian.server.masterdata;

import java.time.LocalDate;

public record ProtectedEmployeeRow(
        String employeeNo,
        String name,
        String mobileCiphertext,
        String mobileHash,
        String mobileLast4,
        long positionId,
        long primaryStoreId,
        LocalDate hireDate,
        boolean canService,
        boolean canSell,
        long createdBy) {
}
