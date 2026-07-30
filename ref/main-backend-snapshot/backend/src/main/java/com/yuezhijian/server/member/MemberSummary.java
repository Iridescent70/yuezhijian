package com.yuezhijian.server.member;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MemberSummary(
        long id,
        String memberNo,
        String fullName,
        String maskedMobile,
        String gender,
        String levelName,
        long ownerStoreId,
        String ownerStoreName,
        BigDecimal availableBalance,
        int availablePoints,
        int cardCount,
        String status,
        LocalDateTime lastVisitAt) {
}
