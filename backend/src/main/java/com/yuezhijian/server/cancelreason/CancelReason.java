package com.yuezhijian.server.cancelreason;

import java.time.LocalDateTime;

public record CancelReason(
        long id,
        String businessType,
        String code,
        String name,
        boolean requiresNote,
        int sortNo,
        String status,
        LocalDateTime updatedAt,
        Long updatedBy,
        String updatedByName,
        String version) {
}
