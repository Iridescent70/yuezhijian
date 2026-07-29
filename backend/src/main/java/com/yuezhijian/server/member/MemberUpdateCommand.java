package com.yuezhijian.server.member;

import java.time.LocalDate;

public record MemberUpdateCommand(
        long id,
        String fullName,
        String nickname,
        String mobile,
        String gender,
        LocalDate birthday,
        String email,
        Long advisorEmployeeId,
        boolean special,
        String version,
        long operatorId) {
}
