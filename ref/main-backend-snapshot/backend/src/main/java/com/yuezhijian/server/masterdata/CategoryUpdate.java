package com.yuezhijian.server.masterdata;

public record CategoryUpdate(
        long id,
        String name,
        int sortNo,
        String status,
        String version,
        long updatedBy) {
}
