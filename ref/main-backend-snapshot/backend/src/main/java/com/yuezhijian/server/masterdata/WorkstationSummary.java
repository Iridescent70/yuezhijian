package com.yuezhijian.server.masterdata;

public record WorkstationSummary(
        long id,
        long storeId,
        String storeName,
        String code,
        String name,
        int capacity,
        int sortNo,
        String status,
        String version) {
}
