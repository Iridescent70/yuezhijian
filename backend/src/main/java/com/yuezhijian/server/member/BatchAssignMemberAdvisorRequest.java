package com.yuezhijian.server.member;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record BatchAssignMemberAdvisorRequest(
        @NotNull(message = "请选择会员") @Size(min = 1, max = 100, message = "单次最多选择100位会员")
        List<@NotNull @Positive Long> memberIds,
        @NotNull(message = "请选择顾问") @Positive Long employeeId) {
    public BatchAssignMemberAdvisorRequest {
        memberIds = memberIds == null ? null : Collections.unmodifiableList(new ArrayList<>(memberIds));
    }
}
