package com.yuezhijian.server.settings;

import java.time.LocalDateTime;

public record SystemParameterItem(
        long id,
        String paramGroup,
        String paramKey,
        String value,
        String valueType,
        String description,
        String status,
        LocalDateTime updatedAt,
        String version) {
}
