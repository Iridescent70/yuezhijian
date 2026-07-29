package com.yuezhijian.server.iam;

import java.util.List;

public record MenuItem(
        long id,
        String code,
        String name,
        String route,
        String icon,
        int sortNo,
        String permission,
        List<MenuItem> children) {
}
