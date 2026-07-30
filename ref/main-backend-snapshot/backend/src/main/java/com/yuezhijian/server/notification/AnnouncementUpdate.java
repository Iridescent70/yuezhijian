package com.yuezhijian.server.notification;

import java.time.LocalDateTime;
import java.util.List;

public record AnnouncementUpdate(
        long id,
        String title,
        String body,
        String scopeType,
        List<Long> storeIds,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        int priority,
        boolean pinned,
        String status,
        String version,
        long operatorId) {
    public AnnouncementUpdate {
        storeIds = List.copyOf(storeIds);
    }
}
