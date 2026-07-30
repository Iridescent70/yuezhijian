package com.yuezhijian.server.iam;

import java.util.List;

public record RoleSummary(
        long id,
        String code,
        String name,
        String dataScope,
        String status,
        List<String> permissions) {
}
