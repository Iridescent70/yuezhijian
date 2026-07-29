package com.yuezhijian.server.iam;

import java.time.LocalDateTime;

public record AccessUserAccount(
        long id,
        String username,
        String passwordHash,
        String fullName,
        String status,
        LocalDateTime lockedAt,
        Long currentStoreId) {
}
