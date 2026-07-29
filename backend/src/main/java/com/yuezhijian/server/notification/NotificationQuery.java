package com.yuezhijian.server.notification;

import java.time.LocalDateTime;

public record NotificationQuery(
        long userId,
        long storeId,
        String messageType,
        String readStatus,
        LocalDateTime publishedFrom,
        LocalDateTime publishedTo,
        LocalDateTime now,
        int page,
        int size) {
    public int offset() {
        return (page - 1) * size;
    }
}
