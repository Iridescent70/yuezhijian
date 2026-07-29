package com.yuezhijian.server.colorstyle;

import java.time.LocalDateTime;
import java.util.List;

public record ColorStyle(
        long id,
        String code,
        String name,
        String description,
        int sortNo,
        String status,
        LocalDateTime updatedAt,
        Long updatedBy,
        String updatedByName,
        String version,
        List<Long> categoryIds,
        List<ColorStyleAsset> assets) {
}
