package com.yuezhijian.server.member;

import java.time.LocalDate;

public record CreateMemberCommand(
        String memberNo,
        String membershipCardNo,
        String fullName,
        String nickname,
        String mobile,
        String gender,
        LocalDate birthday,
        String email,
        String sourceType,
        long joinStoreId,
        long ownerStoreId,
        Long advisorEmployeeId,
        long createdBy) {
}
