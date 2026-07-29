package com.yuezhijian.server.member;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MemberDetail(
        long id,
        String memberNo,
        String membershipCardNo,
        String fullName,
        String nickname,
        String maskedMobile,
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
        boolean special,
        String status,
        LocalDateTime lastVisitAt,
        LocalDateTime createdAt,
        MemberAssets assets,
        List<MemberTag> tags,
        String version) {
    public MemberDetail {
        tags = List.copyOf(tags);
    }
}
