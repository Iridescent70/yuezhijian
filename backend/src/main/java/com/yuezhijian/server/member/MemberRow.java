package com.yuezhijian.server.member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MemberRow(
        long id,
        String memberNo,
        String membershipCardNo,
        String fullName,
        String nickname,
        String mobileLast4,
        String gender,
        LocalDate birthday,
        String email,
        String sourceType,
        long joinStoreId,
        String joinStoreName,
        long ownerStoreId,
        String ownerStoreName,
        Long advisorEmployeeId,
        String levelName,
        boolean specialFlag,
        String status,
        LocalDateTime lastVisitAt,
        LocalDateTime createdAt,
        BigDecimal availableBalance,
        BigDecimal frozenBalance,
        BigDecimal totalRecharged,
        int availablePoints,
        int lifetimePoints,
        int cardCount,
        byte[] rowVersion) {
}
