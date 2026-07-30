package com.yuezhijian.server.colorstyle;

import java.util.List;

public record ColorStyleUpdate(
        long id,
        String name,
        String description,
        int sortNo,
        String status,
        List<Long> categoryIds,
        String version,
        long operatorId) {
}
