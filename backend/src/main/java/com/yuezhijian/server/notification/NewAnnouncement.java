package com.yuezhijian.server.notification;

import java.time.LocalDateTime;
import java.util.List;

public record NewAnnouncement(
        String notificationNo,
        String title,
        String body,
        String scopeType,
        List<Long> storeIds,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        int priority,
        boolean pinned,
        String status,
        long operatorId) {
    public NewAnnouncement {
        storeIds = List.copyOf(storeIds);
    }
}
