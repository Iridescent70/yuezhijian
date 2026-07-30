package com.yuezhijian.server.masterdata;

public record NewWorkstation(
        long storeId,
        String code,
        String name,
        int capacity,
        int sortNo,
        long createdBy) {
}
