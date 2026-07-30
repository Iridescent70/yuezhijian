package com.yuezhijian.server.masterdata;

import java.time.LocalDate;

public record ProtectedEmployeeUpdate(
        long id,
        String name,
        boolean mobileChanged,
        String mobileCiphertext,
        String mobileHash,
        String mobileLast4,
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
