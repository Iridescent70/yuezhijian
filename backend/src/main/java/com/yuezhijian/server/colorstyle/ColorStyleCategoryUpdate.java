package com.yuezhijian.server.colorstyle;

public record ColorStyleCategoryUpdate(
        long id,
        Long parentId,
        String name,
        int sortNo,
        String status,
        String version,
        long operatorId) {
}
