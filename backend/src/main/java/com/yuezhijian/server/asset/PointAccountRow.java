package com.yuezhijian.server.asset;

import java.time.LocalDateTime;

record PointAccountRow(
        long accountId,
        long memberId,
        int availablePoints,
        int lifetimePoints,
        LocalDateTime lastTransactionAt,
        byte[] rowVersion) {}
