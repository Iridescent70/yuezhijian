package com.yuezhijian.server.colorstyle;

import java.util.List;

public record NewColorStyle(
        String code,
        String name,
        String description,
        int sortNo,
        List<Long> categoryIds,
        long operatorId) {
}
