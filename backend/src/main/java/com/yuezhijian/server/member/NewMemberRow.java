package com.yuezhijian.server.member;

import java.time.LocalDate;

public record NewMemberRow(
        String memberNo,
        String fullName,
        String nickname,
        String gender,
        LocalDate birthday,
        String mobileCiphertext,
        String mobileHash,
        String mobileLast4,
        String email,
        String sourceType,
        long joinStoreId,
        long ownerStoreId,
        Long advisorEmployeeId,
        Long levelId,
        long createdBy) {
}
