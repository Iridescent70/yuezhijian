package com.yuezhijian.server.member;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryOwnershipAdjustmentRepository implements OwnershipAdjustmentRepository {
    private final AtomicLong ids = new AtomicLong();
    private final List<OwnershipAdjustmentRow> adjustments = new ArrayList<>();

    @Override
    public synchronized List<OwnershipAdjustmentRow> search(OwnershipAdjustmentQuery query) {
        return adjustments.stream()
                .filter(item -> query.memberId() == null || item.memberId() == query.memberId())
                .filter(item -> query.approvalStatus() == null
                        || item.approvalStatus().equals(query.approvalStatus()))
                .filter(item -> query.executionStatus() == null
                        || item.executionStatus().equals(query.executionStatus()))
                .sorted(Comparator.comparing(OwnershipAdjustmentRow::requestedAt).reversed()
                        .thenComparing(OwnershipAdjustmentRow::id, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public synchronized Optional<OwnershipAdjustmentRow> findById(long id) {
        return adjustments.stream().filter(item -> item.id() == id).findFirst();
    }

    @Override
    public synchronized boolean hasActiveAdjustment(long memberId) {
        return adjustments.stream().anyMatch(item -> item.memberId() == memberId
                && List.of("WAITING", "PROCESSING").contains(item.executionStatus()));
    }

    @Override
    public synchronized OwnershipAdjustmentRow create(OwnershipAdjustmentDraft draft) {
        if (hasActiveAdjustment(draft.memberId())) {
            throw new DuplicateResourceException("会员已有待处理归属调整");
        }
        OwnershipAdjustmentRow created = new OwnershipAdjustmentRow(
                ids.incrementAndGet(), draft.adjustmentNo(), draft.memberId(), draft.memberNo(), draft.memberName(),
                draft.oldStoreId(), draft.oldStoreName(), draft.newStoreId(), draft.newStoreName(),
                draft.effectiveDate(), draft.shareRuleJson(), draft.reason(), "PENDING", "WAITING",
                draft.requestedBy(), LocalDateTime.now(), null, null, null, null, null, "1");
        adjustments.add(created);
        return created;
    }

    @Override
    public synchronized OwnershipAdjustmentRow review(
            long id, boolean approved, String comment, String version, long operatorId) {
        OwnershipAdjustmentRow old = requireVersion(id, version);
        if (!"PENDING".equals(old.approvalStatus()) || !"WAITING".equals(old.executionStatus())) {
            throw stale();
        }
        OwnershipAdjustmentRow updated = copy(
                old, approved ? "APPROVED" : "REJECTED", approved ? "WAITING" : "CANCELLED",
                operatorId, LocalDateTime.now(), comment, null, null, nextVersion(old));
        replace(updated);
        return updated;
    }

    @Override
    public synchronized List<OwnershipAdjustmentRow> due(LocalDate businessDate) {
        return adjustments.stream()
                .filter(item -> "APPROVED".equals(item.approvalStatus()))
                .filter(item -> "WAITING".equals(item.executionStatus()))
                .filter(item -> !item.effectiveDate().isAfter(businessDate))
                .sorted(Comparator.comparing(OwnershipAdjustmentRow::effectiveDate)
                        .thenComparingLong(OwnershipAdjustmentRow::id))
                .toList();
    }

    @Override
    public synchronized Optional<OwnershipAdjustmentRow> claim(
            long id, String version, LocalDate businessDate) {
        OwnershipAdjustmentRow old = findById(id).orElse(null);
        if (old == null || !old.version().equals(version)
                || !"APPROVED".equals(old.approvalStatus())
                || !"WAITING".equals(old.executionStatus())
                || old.effectiveDate().isAfter(businessDate)) return Optional.empty();
        OwnershipAdjustmentRow claimed = copy(
                old, old.approvalStatus(), "PROCESSING", old.reviewedBy(), old.reviewedAt(),
                old.reviewComment(), null, null, nextVersion(old));
        replace(claimed);
        return Optional.of(claimed);
    }

    @Override
    public synchronized OwnershipAdjustmentRow finish(
            long id, boolean applied, String message, String version) {
        OwnershipAdjustmentRow old = requireVersion(id, version);
        if (!"PROCESSING".equals(old.executionStatus())) throw stale();
        OwnershipAdjustmentRow finished = copy(
                old, old.approvalStatus(), applied ? "APPLIED" : "FAILED", old.reviewedBy(), old.reviewedAt(),
                old.reviewComment(), applied ? LocalDateTime.now() : null, message, nextVersion(old));
        replace(finished);
        return finished;
    }

    private OwnershipAdjustmentRow requireVersion(long id, String version) {
        OwnershipAdjustmentRow row = findById(id).orElseThrow();
        if (!row.version().equals(version)) throw stale();
        return row;
    }

    private void replace(OwnershipAdjustmentRow row) {
        adjustments.removeIf(item -> item.id() == row.id());
        adjustments.add(row);
    }

    private String nextVersion(OwnershipAdjustmentRow row) {
        return String.valueOf(Long.parseLong(row.version()) + 1);
    }

    private OwnershipAdjustmentRow copy(
            OwnershipAdjustmentRow old,
            String approvalStatus,
            String executionStatus,
            Long reviewedBy,
            LocalDateTime reviewedAt,
            String reviewComment,
            LocalDateTime appliedAt,
            String executionMessage,
            String version) {
        return new OwnershipAdjustmentRow(
                old.id(), old.adjustmentNo(), old.memberId(), old.memberNo(), old.memberName(),
                old.oldStoreId(), old.oldStoreName(), old.newStoreId(), old.newStoreName(), old.effectiveDate(),
                old.shareRuleJson(), old.reason(), approvalStatus, executionStatus, old.requestedBy(), old.requestedAt(),
                reviewedBy, reviewedAt, reviewComment, appliedAt, executionMessage, version);
    }

    private DuplicateResourceException stale() {
        return new DuplicateResourceException("归属调整已被他人处理，请刷新后重试");
    }
}
