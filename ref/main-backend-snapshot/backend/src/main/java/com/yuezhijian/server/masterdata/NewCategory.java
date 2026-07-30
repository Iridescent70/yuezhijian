package com.yuezhijian.server.masterdata;

public record NewCategory(
        String type,
        String code,
        String name,
        String path,
        int sortNo,
        long createdBy) {
}
