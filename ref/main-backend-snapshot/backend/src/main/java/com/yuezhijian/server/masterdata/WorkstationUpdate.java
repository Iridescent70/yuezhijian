package com.yuezhijian.server.masterdata;

public record WorkstationUpdate(
        long id,
        String name,
        int capacity,
        int sortNo,
        String status,
        String version,
        long updatedBy) {
}
