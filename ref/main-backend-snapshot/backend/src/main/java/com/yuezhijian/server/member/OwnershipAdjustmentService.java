package com.yuezhijian.server.member;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreDataScope;
import com.yuezhijian.server.iam.StoreSummary;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnershipAdjustmentService {
    private static final Set<String> APPROVAL_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");
    private static final Set<String> EXECUTION_STATUSES =
            Set.of("WAITING", "PROCESSING", "APPLIED", "FAILED", "CANCELLED");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final OwnershipAdjustmentRepository repository;
    private final MemberRepository members;
    private final MemberService memberService;
    private final BusinessNumberGenerator numbers;
    private final AccessCatalogService accessCatalog;
    private final StoreDataScope storeDataScope;
    private final ObjectMapper objectMapper;

    public OwnershipAdjustmentService(
            OwnershipAdjustmentRepository repository,
            MemberRepository members,
            MemberService memberService,
            BusinessNumberGenerator numbers,
            AccessCatalogService accessCatalog,
            StoreDataScope storeDataScope,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.members = members;
        this.memberService = memberService;
        this.numbers = numbers;
        this.accessCatalog = accessCatalog;
        this.storeDataScope = storeDataScope;
        this.objectMapper = objectMapper;
    }

    public List<OwnershipAdjustment> search(Long memberId, String approvalStatus, String executionStatus) {
        if (memberId != null) memberService.detail(memberId);
        String approval = normalizeStatus(approvalStatus, APPROVAL_STATUSES, "审批状态不正确");
        String execution = normalizeStatus(executionStatus, EXECUTION_STATUSES, "执行状态不正确");
        return repository.search(new OwnershipAdjustmentQuery(memberId, approval, execution)).stream()
                .filter(row -> storeDataScope.canAccess(row.oldStoreId())
                        || storeDataScope.canAccess(row.newStoreId()))
                .map(this::toAdjustment).toList();
    }

    public OwnershipAdjustment detail(long id) {
        OwnershipAdjustmentRow row = requireRow(id);
        storeDataScope.requireAny(List.of(row.oldStoreId(), row.newStoreId()));
        return toAdjustment(row);
    }

    @Transactional
    public OwnershipAdjustment create(
            long memberId, CreateOwnershipAdjustmentRequest request, String username) {
        MemberDetail member = memberService.detail(memberId);
        if (!member.version().equals(request.memberVersion())) {
            throw new DuplicateResourceException("会员档案已被他人修改，请刷新后重试");
        }
        StoreSummary newStore = requireActiveStore(request.newStoreId());
        storeDataScope.require(newStore.id());
        if (member.ownerStoreId() == newStore.id()) {
            throw new IllegalArgumentException("新归属门店不能与当前归属门店相同");
        }
        if (request.effectiveDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("生效日期不能早于今天，历史业绩不会追溯改写");
        }
        if (repository.hasActiveAdjustment(memberId)) {
            throw new DuplicateResourceException("会员已有待审批或待生效的归属调整");
        }
        String ruleJson = writeShareRule(request.shareRule());
        long operatorId = accessCatalog.userIdentity(username).id();
        return toAdjustment(repository.create(new OwnershipAdjustmentDraft(
                numbers.nextOwnershipAdjustmentNo(), member.id(), member.memberNo(), member.fullName(),
                member.ownerStoreId(), member.ownerStoreName(), newStore.id(), newStore.name(),
                request.effectiveDate(), ruleJson, request.reason().trim(), member.version(), operatorId)));
    }

    @Transactional
    public OwnershipAdjustment review(
            long id, boolean approved, ReviewOwnershipAdjustmentRequest request, String username) {
        OwnershipAdjustmentRow current = requireRow(id);
        storeDataScope.require(current.oldStoreId());
        storeDataScope.require(current.newStoreId());
        if (!"PENDING".equals(current.approvalStatus()) || !"WAITING".equals(current.executionStatus())) {
            throw new IllegalArgumentException("当前归属调整不可重复审批");
        }
        String comment = blankToNull(request.comment());
        if (!approved && comment == null) throw new IllegalArgumentException("驳回时必须填写意见");
        long operatorId = accessCatalog.userIdentity(username).id();
        OwnershipAdjustmentRow reviewed = repository.review(
                id, approved, comment, request.version(), operatorId);
        if (approved && !reviewed.effectiveDate().isAfter(LocalDate.now())) {
            reviewed = apply(reviewed, LocalDate.now());
        }
        return toAdjustment(reviewed);
    }

    @Transactional
    public int applyDueOwnershipChanges() {
        return applyDueOwnershipChanges(LocalDate.now());
    }

    @Transactional
    public int applyDueOwnershipChanges(LocalDate businessDate) {
        int count = 0;
        for (OwnershipAdjustmentRow row : repository.due(businessDate)) {
            OwnershipAdjustmentRow result = apply(row, businessDate);
            if ("APPLIED".equals(result.executionStatus())) count++;
        }
        return count;
    }

    private OwnershipAdjustmentRow apply(OwnershipAdjustmentRow waiting, LocalDate businessDate) {
        var claimed = repository.claim(waiting.id(), waiting.version(), businessDate);
        if (claimed.isEmpty()) return requireRow(waiting.id());
        OwnershipAdjustmentRow row = claimed.get();
        long operatorId = row.reviewedBy() == null ? row.requestedBy() : row.reviewedBy();
        boolean applied = members.applyOwnership(
                row.memberId(), row.oldStoreId(), row.newStoreId(), operatorId);
        String message = applied
                ? "归属门店已按生效日切换；后续业务使用新门店，历史单据保持原归属"
                : "执行失败：会员当前归属门店与申请快照不一致，未覆盖现有数据";
        return repository.finish(row.id(), applied, message, row.version());
    }

    private OwnershipAdjustmentRow requireRow(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("归属调整不存在"));
    }

    private StoreSummary requireActiveStore(long id) {
        return accessCatalog.stores().stream()
                .filter(store -> store.id() == id && "ACTIVE".equals(store.status()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("新归属门店不存在或已停用"));
    }

    private String writeShareRule(Map<String, Object> rule) {
        try {
            String json = objectMapper.writeValueAsString(new LinkedHashMap<>(rule));
            if (json.length() > 20000) throw new IllegalArgumentException("分润规则快照不能超过20000个字符");
            return json;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("分润规则快照不是有效JSON对象");
        }
    }

    private OwnershipAdjustment toAdjustment(OwnershipAdjustmentRow row) {
        Map<String, Object> rule;
        try {
            rule = objectMapper.readValue(row.shareRuleJson(), MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("归属调整分润规则快照无效");
        }
        return new OwnershipAdjustment(
                row.id(), row.adjustmentNo(), row.memberId(), row.memberNo(), row.memberName(),
                row.oldStoreId(), row.oldStoreName(), row.newStoreId(), row.newStoreName(),
                row.effectiveDate(), rule, row.reason(), row.approvalStatus(), row.executionStatus(),
                row.requestedBy(), row.requestedAt(), row.reviewedBy(), row.reviewedAt(),
                row.reviewComment(), row.appliedAt(), row.executionMessage(), row.version());
    }

    private static String normalizeStatus(String value, Set<String> allowed, String message) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
