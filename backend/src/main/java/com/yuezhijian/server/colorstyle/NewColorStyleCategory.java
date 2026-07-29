package com.yuezhijian.server.colorstyle;

public record NewColorStyleCategory(
        Long parentId,
        String code,
        String name,
        int sortNo,
        long operatorId) {
}
