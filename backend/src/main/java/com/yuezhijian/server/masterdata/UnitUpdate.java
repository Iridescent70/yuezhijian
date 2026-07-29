package com.yuezhijian.server.masterdata;

public record UnitUpdate(
        long id,
        String name,
        int decimalPlaces,
        String status,
        String version,
        long updatedBy) {
}
