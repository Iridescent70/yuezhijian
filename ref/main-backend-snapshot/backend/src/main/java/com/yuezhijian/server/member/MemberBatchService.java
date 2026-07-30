package com.yuezhijian.server.member;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MemberBatchService {
    private final MemberService members;

    public MemberBatchService(MemberService members) {
        this.members = members;
    }

    public MemberBatchResult freeze(BatchFreezeMembersRequest request, String username) {
        List<MemberBatchItemResult> results = new ArrayList<>();
        for (Long id : distinct(request.memberIds())) {
            MemberDetail current = find(id, results);
            if (current == null) continue;
            if ("FROZEN".equals(current.status())) {
                results.add(skipped(current, "会员已经冻结，未重复写入状态历史"));
                continue;
            }
            if (!"ACTIVE".equals(current.status())) {
                results.add(failed(current, "只有正常状态会员可以批量冻结"));
                continue;
            }
            try {
                members.changeStatus(id, new ChangeMemberStatusRequest(
                        "FROZEN", request.reason().trim(), current.version()), username);
                results.add(success(current, "已冻结"));
            } catch (DuplicateResourceException | IllegalArgumentException exception) {
                results.add(failed(current, exception.getMessage()));
            }
        }
        return MemberBatchResult.of("FREEZE", results);
    }

    public MemberBatchResult updateTags(BatchUpdateMemberTagsRequest request, String username) {
        List<Long> addIds = distinct(request.addIds());
        List<Long> removeIds = distinct(request.removeIds());
        if (addIds.isEmpty() && removeIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要添加或移除的标签");
        }
        if (addIds.stream().anyMatch(removeIds::contains)) {
            throw new IllegalArgumentException("同一标签不能同时添加和移除");
        }
        Set<Long> available = members.tagOptions().stream()
                .map(MemberTagOption::id).collect(java.util.stream.Collectors.toSet());
        if (!available.containsAll(addIds) || !available.containsAll(removeIds)) {
            throw new IllegalArgumentException("标签不存在或已停用");
        }

        List<MemberBatchItemResult> results = new ArrayList<>();
        for (Long id : distinct(request.memberIds())) {
            MemberDetail current = find(id, results);
            if (current == null) continue;
            Set<Long> currentIds = current.tags().stream()
                    .map(MemberTag::id).collect(java.util.stream.Collectors.toSet());
            List<Long> effectiveAdd = addIds.stream().filter(tagId -> !currentIds.contains(tagId)).toList();
            List<Long> effectiveRemove = removeIds.stream().filter(currentIds::contains).toList();
            if (effectiveAdd.isEmpty() && effectiveRemove.isEmpty()) {
                results.add(skipped(current, "标签已经是目标状态"));
                continue;
            }
            try {
                members.updateTags(id, new UpdateMemberTagsRequest(
                        effectiveAdd, effectiveRemove, current.version()), username);
                results.add(success(current, "标签已更新"));
            } catch (DuplicateResourceException | IllegalArgumentException exception) {
                results.add(failed(current, exception.getMessage()));
            }
        }
        return MemberBatchResult.of("UPDATE_TAGS", results);
    }

    public MemberBatchResult assignAdvisor(BatchAssignMemberAdvisorRequest request, String username) {
        List<MemberBatchItemResult> results = new ArrayList<>();
        for (Long id : distinct(request.memberIds())) {
            MemberDetail current = find(id, results);
            if (current == null) continue;
            if (java.util.Objects.equals(current.advisorEmployeeId(), request.employeeId())) {
                results.add(skipped(current, "已经由该顾问负责"));
                continue;
            }
            try {
                members.assignAdvisor(id, request.employeeId(), current.version(), username);
                results.add(success(current, "顾问已分配"));
            } catch (DuplicateResourceException | IllegalArgumentException exception) {
                results.add(failed(current, exception.getMessage()));
            }
        }
        return MemberBatchResult.of("ASSIGN_ADVISOR", results);
    }

    private MemberDetail find(long id, List<MemberBatchItemResult> results) {
        try {
            return members.detail(id);
        } catch (ResourceNotFoundException exception) {
            results.add(new MemberBatchItemResult(id, null, null, "FAILED", exception.getMessage()));
            return null;
        }
    }

    private static List<Long> distinct(List<Long> ids) {
        return new LinkedHashSet<>(ids).stream().toList();
    }

    private static MemberBatchItemResult success(MemberDetail member, String message) {
        return item(member, "SUCCESS", message);
    }

    private static MemberBatchItemResult skipped(MemberDetail member, String message) {
        return item(member, "SKIPPED", message);
    }

    private static MemberBatchItemResult failed(MemberDetail member, String message) {
        return item(member, "FAILED", message);
    }

    private static MemberBatchItemResult item(MemberDetail member, String status, String message) {
        return new MemberBatchItemResult(
                member.id(), member.memberNo(), member.fullName(), status, message);
    }
}
