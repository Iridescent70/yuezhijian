package com.yuezhijian.server.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateMemberRequest(
        @NotBlank(message = "请输入会员姓名") @Size(max = 100) String fullName,
        @Size(max = 100) String nickname,
        String mobile,
        @NotBlank(message = "请选择性别") String gender,
        LocalDate birthday,
        @Email(message = "邮箱格式不正确") @Size(max = 255) String email,
        Long advisorEmployeeId,
        @NotNull(message = "请选择是否为特殊会员") Boolean special,
        @NotBlank(message = "缺少会员数据版本") String version) {
}
