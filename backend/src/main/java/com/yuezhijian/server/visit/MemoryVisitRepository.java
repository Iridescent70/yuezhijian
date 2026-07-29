package com.yuezhijian.server.visit;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryVisitRepository implements VisitRepository {
    private final AtomicLong taskIds = new AtomicLong();
    private final AtomicLong participantIds = new AtomicLong();
    private final AtomicLong recordIds = new AtomicLong();
    private final List<VisitTaskDetail> tasks = new ArrayList<>();

    @Override
    public synchronized List<VisitTaskSummary> tasks(VisitTaskQuery query) {
        String keyword = query.keyword() == null ? null : query.keyword().toLowerCase(Locale.ROOT);
        return tasks.stream().map(this::refresh).map(VisitTaskDetail::task)
                .filter(item -> query.storeId() == null || item.storeId() == query.storeId())
                .filter(item -> query.employeeId() == null || participantFor(item.id(), query.employeeId()))
                .filter(item -> query.status() == null || query.status().equals(item.status()))
                .filter(item -> query.dueDate() == null || item.dueAt().toLocalDate().equals(query.dueDate()))
                .filter(item -> keyword == null || item.taskNo().toLowerCase(Locale.ROOT).contains(keyword)
                        || item.billNo().toLowerCase(Locale.ROOT).contains(keyword)
                        || item.customerName().toLowerCase(Locale.ROOT).contains(keyword)
                        || item.maskedMobile().contains(keyword))
                .sorted(Comparator.comparing(VisitTaskSummary::dueAt).thenComparingLong(VisitTaskSummary::id))
                .toList();
    }

    @Override
    public synchronized Optional<VisitTaskDetail> findById(long id) {
        return tasks.stream().filter(item -> item.task().id() == id).findFirst().map(this::refresh);
    }

    @Override
    public synchronized Optional<VisitTaskDetail> findByBillId(long billId) {
        return tasks.stream().filter(item -> item.task().billId() == billId).findFirst().map(this::refresh);
    }

    @Override
    public synchronized VisitTaskDetail create(VisitTaskDraft draft) {
        Optional<VisitTaskDetail> existing = findByBillId(draft.billId());
        if (existing.isPresent()) return existing.get();
        if (tasks.stream().anyMatch(item -> item.task().taskNo().equals(draft.taskNo()))) {
            throw new DuplicateResourceException("回访任务编号已存在");
        }
        long taskId = taskIds.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        List<VisitParticipantItem> participants = draft.participants().stream()
                .map(item -> new VisitParticipantItem(
                        participantIds.incrementAndGet(), item.employeeId(), item.employeeName(),
                        item.serviceSummary(), "PENDING", null))
                .toList();
        VisitTaskSummary summary = new VisitTaskSummary(
                taskId, draft.taskNo(), draft.memberId(), draft.billId(), draft.billNo(), draft.customerName(),
                draft.maskedMobile(), draft.storeId(), draft.storeName(), draft.dueAt(), "AFTER_SALE", "PENDING",
                false, false, participants.size(), 0, null, draft.settledAt(), null, null, null, now);
        VisitTaskDetail detail = new VisitTaskDetail(summary, participants, List.of());
        tasks.add(detail);
        return detail;
    }

    @Override
    public synchronized VisitTaskDetail claimParticipant(
            long taskId, long participantId, long employeeId, String employeeName) {
        VisitTaskDetail current = required(taskId);
        if (current.participants().stream().anyMatch(item -> item.employeeId() != null && item.employeeId() == employeeId)) {
            throw new DuplicateResourceException("该员工已在回访参与人中");
        }
        List<VisitParticipantItem> participants = current.participants().stream().map(item -> {
            if (item.id() != participantId) return item;
            if (item.employeeId() != null) throw new DuplicateResourceException("回访任务已被其他员工认领");
            return new VisitParticipantItem(
                    item.id(), employeeId, employeeName, item.serviceSummary(), item.status(), item.completedAt());
        }).toList();
        VisitTaskDetail updated = new VisitTaskDetail(current.task(), participants, current.records());
        replace(updated);
        return refresh(updated);
    }

    @Override
    public synchronized VisitTaskDetail appendRecord(VisitRecordDraft draft) {
        VisitTaskDetail current = required(draft.taskId());
        VisitParticipantItem participant = current.participants().stream()
                .filter(item -> item.id() == draft.participantId()).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("回访参与人不存在"));
        if ("COMPLETED".equals(participant.status())) throw new DuplicateResourceException("该技师回访已完成");
        LocalDateTime now = LocalDateTime.now();
        List<VisitRecordItem> records = new ArrayList<>(current.records());
        records.add(new VisitRecordItem(
                recordIds.incrementAndGet(), draft.participantId(), draft.employeeId(), draft.employeeName(),
                draft.resultCode(), draft.satisfactionScore(), draft.complaintFlag(), draft.content(),
                draft.nextFollowAt(), now, draft.createdBy(), "本地管理员"));
        List<VisitParticipantItem> participants = current.participants().stream()
                .map(item -> item.id() == draft.participantId() && draft.completesParticipant()
                        ? new VisitParticipantItem(
                                item.id(), item.employeeId(), item.employeeName(), item.serviceSummary(),
                                "COMPLETED", now)
                        : item)
                .toList();
        int completedCount = (int) participants.stream().filter(item -> "COMPLETED".equals(item.status())).count();
        boolean completed = completedCount == participants.size();
        VisitTaskSummary task = current.task();
        LocalDateTime dueAt = draft.nextFollowAt() == null ? task.dueAt() : draft.nextFollowAt();
        VisitTaskSummary summary = copyTask(
                task, dueAt, completed ? "COMPLETED" : "PENDING",
                task.complaintFlag() || draft.complaintFlag(), completedCount,
                task.conclusion(), completed ? now : null, task.canceledAt(), task.cancelReason());
        VisitTaskDetail updated = new VisitTaskDetail(summary, participants, records);
        replace(updated);
        return refresh(updated);
    }

    @Override
    public synchronized VisitTaskDetail complete(long taskId, String conclusion, long operatorId) {
        VisitTaskDetail current = required(taskId);
        if (current.participants().stream().anyMatch(item -> !"COMPLETED".equals(item.status()))) {
            throw new DuplicateResourceException("仍有未完成的回访参与人");
        }
        VisitTaskSummary task = current.task();
        VisitTaskSummary summary = copyTask(
                task, task.dueAt(), "COMPLETED", task.complaintFlag(), task.participantCount(), conclusion,
                task.completedAt() == null ? LocalDateTime.now() : task.completedAt(), null, null);
        VisitTaskDetail updated = new VisitTaskDetail(summary, current.participants(), current.records());
        replace(updated);
        return updated;
    }

    @Override
    public synchronized void cancelPendingByBill(long billId, String reason, long operatorId) {
        findByBillId(billId).ifPresent(current -> {
            if (!SetHelper.pending(current.task().status())) return;
            VisitTaskSummary task = current.task();
            VisitTaskSummary summary = copyTask(
                    task, task.dueAt(), "CANCELLED", task.complaintFlag(), task.completedCount(), task.conclusion(),
                    null, LocalDateTime.now(), reason);
            replace(new VisitTaskDetail(summary, current.participants(), current.records()));
        });
    }

    private boolean participantFor(long taskId, long employeeId) {
        return required(taskId).participants().stream()
                .anyMatch(item -> item.employeeId() != null && item.employeeId() == employeeId);
    }

    private VisitTaskDetail required(long id) {
        return tasks.stream().filter(item -> item.task().id() == id).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("回访任务不存在"));
    }

    private void replace(VisitTaskDetail detail) {
        tasks.removeIf(item -> item.task().id() == detail.task().id());
        tasks.add(detail);
    }

    private VisitTaskDetail refresh(VisitTaskDetail detail) {
        VisitTaskSummary task = detail.task();
        boolean overdue = SetHelper.pending(task.status()) && task.dueAt().isBefore(LocalDateTime.now());
        String status = overdue ? "OVERDUE" : task.status();
        if (overdue == task.overdue() && status.equals(task.status())) return detail;
        return new VisitTaskDetail(copyTask(
                task, task.dueAt(), status, task.complaintFlag(), task.completedCount(), task.conclusion(),
                task.completedAt(), task.canceledAt(), task.cancelReason()), detail.participants(), detail.records());
    }

    private VisitTaskSummary copyTask(
            VisitTaskSummary task,
            LocalDateTime dueAt,
            String status,
            boolean complaintFlag,
            int completedCount,
            String conclusion,
            LocalDateTime completedAt,
            LocalDateTime canceledAt,
            String cancelReason) {
        boolean overdue = SetHelper.pending(status) && dueAt.isBefore(LocalDateTime.now());
        return new VisitTaskSummary(
                task.id(), task.taskNo(), task.memberId(), task.billId(), task.billNo(), task.customerName(),
                task.maskedMobile(), task.storeId(), task.storeName(), dueAt, task.taskType(),
                overdue ? "OVERDUE" : status, overdue, complaintFlag, task.participantCount(), completedCount,
                conclusion, task.settledAt(), completedAt, canceledAt, cancelReason, task.createdAt());
    }

    private static final class SetHelper {
        private static boolean pending(String status) {
            return "PENDING".equals(status) || "OVERDUE".equals(status);
        }
    }
}
