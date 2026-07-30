package com.yuezhijian.server.asset;

import java.time.LocalDateTime;

public record PointAccount(
        long memberId,
        int availablePoints,
        int lifetimePoints,
        LocalDateTime lastTransactionAt,
        String version) {}
