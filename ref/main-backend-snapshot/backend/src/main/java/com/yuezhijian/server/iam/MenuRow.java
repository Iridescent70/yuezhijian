package com.yuezhijian.server.iam;

public record MenuRow(
        long id,
        Long parentId,
        String code,
        String name,
        String route,
        String icon,
        int sortNo,
        String permission) {
}
