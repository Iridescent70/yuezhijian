package com.yuezhijian.server.member;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record BatchUpdateMemberTagsRequest(
        @NotNull(message = "请选择会员") @Size(min = 1, max = 100, message = "单次最多选择100位会员")
        List<@NotNull @Positive Long> memberIds,
        @NotNull(message = "缺少待添加标签") @Size(max = 100, message = "单次最多添加100个标签")
        List<@NotNull @Positive Long> addIds,
        @NotNull(message = "缺少待移除标签") @Size(max = 100, message = "单次最多移除100个标签")
        List<@NotNull @Positive Long> removeIds) {
    public BatchUpdateMemberTagsRequest {
        memberIds = memberIds == null ? null : Collections.unmodifiableList(new ArrayList<>(memberIds));
        addIds = addIds == null ? null : Collections.unmodifiableList(new ArrayList<>(addIds));
        removeIds = removeIds == null ? null : Collections.unmodifiableList(new ArrayList<>(removeIds));
    }
}
