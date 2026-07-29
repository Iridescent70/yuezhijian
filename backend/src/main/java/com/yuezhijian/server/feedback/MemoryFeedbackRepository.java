package com.yuezhijian.server.feedback;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.time.Duration;
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
public class MemoryFeedbackRepository implements FeedbackRepository {
    private final AtomicLong feedbackIds = new AtomicLong();
    private final AtomicLong actionIds = new AtomicLong();
    private final List<FeedbackDetail> feedback = new ArrayList<>();

    @Override
    public synchronized List<FeedbackSummary> feedback(FeedbackQuery query) {
        String keyword = query.keyword() == null ? null : query.keyword().toLowerCase(Locale.ROOT);
        return feedback.stream().map(FeedbackDetail::feedback).map(this::withTiming)
                .filter(item -> query.storeId() == null || item.storeId() == query.storeId())
                .filter(item -> query.handlerId() == null || java.util.Objects.equals(item.handlerId(), query.handlerId()))
                .filter(item -> query.score() == null || java.util.Objects.equals(item.score(), query.score()))
                .filter(item -> query.status() == null || item.status().equals(query.status()))
                .filter(item -> query.overdue() == null || item.overdue() == query.overdue())
                .filter(item -> keyword == null || item.feedbackNo().toLowerCase(Locale.ROOT).contains(keyword)
                        || item.billNo().toLowerCase(Locale.ROOT).contains(keyword)
                        || item.memberName().toLowerCase(Locale.ROOT).contains(keyword)
                        || item.maskedMobile().contains(keyword))
                .sorted(Comparator.comparing(FeedbackSummary::overdue).reversed()
                        .thenComparing(FeedbackSummary::updatedAt, Comparator.reverseOrder())
                        .thenComparing(FeedbackSummary::id, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public synchronized Optional<FeedbackDetail> findById(long id) {
        return feedback.stream().filter(item -> item.feedback().id() == id).findFirst()
                .map(item -> new FeedbackDetail(withTiming(item.feedback()), item.actions()));
    }

    @Override
    public synchronized Optional<FeedbackDetail> findByVisitRecordId(long visitRecordId) {
        return feedback.stream().filter(item -> item.feedback().visitRecordId() == visitRecordId).findFirst()
                .map(item -> new FeedbackDetail(withTiming(item.feedback()), item.actions()));
    }

    @Override
    public synchronized FeedbackDetail create(FeedbackDraft draft) {
        Optional<FeedbackDetail> existing = findByVisitRecordId(draft.visitRecordId());
        if (existing.isPresent()) return existing.get();
        if (feedback.stream().anyMatch(item -> item.feedback().feedbackNo().equals(draft.feedbackNo()))) {
            throw new DuplicateResourceException("服务反馈编号已存在");
        }
        long id = feedbackIds.incrementAndGet();
        LocalDateTime now = draft.createdAt() == null ? LocalDateTime.now() : draft.createdAt();
        FeedbackSummary summary = new FeedbackSummary(
                id, draft.feedbackNo(), draft.visitTaskId(), draft.visitRecordId(), draft.memberId(),
                draft.memberName(), draft.maskedMobile(), draft.billId(), draft.billNo(), draft.storeId(),
                draft.storeName(), "VISIT", draft.score(), draft.content(), "SERVICE", "OPEN",
                null, null, null, null, null, null, draft.dueHours(), draft.dueAt(), false, 0, 1, now, now);
        FeedbackActionItem created = new FeedbackActionItem(
                actionIds.incrementAndGet(), "CREATED", null, "OPEN", null, null,
                "回访标记客诉后自动建单", now, draft.createdBy(), "本地管理员");
        FeedbackDetail detail = new FeedbackDetail(summary, List.of(created));
        feedback.add(detail);
        return detail;
    }

    @Override
    public synchronized FeedbackDetail update(FeedbackUpdate update) {
        FeedbackDetail current = findById(update.id())
                .orElseThrow(() -> new ResourceNotFoundException("服务反馈不存在"));
        if (!current.feedback().status().equals(update.expectedStatus())) {
            throw new DuplicateResourceException("服务反馈已被他人处理，请刷新后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        FeedbackSummary item = current.feedback();
        LocalDateTime handledAt = item.handledAt();
        LocalDateTime resolvedAt = item.resolvedAt();
        LocalDateTime closedAt = item.closedAt();
        if ("RESOLVED".equals(update.actionType())) {
            handledAt = now;
            resolvedAt = now;
        } else if ("CLOSED".equals(update.actionType())) {
            handledAt = handledAt == null ? now : handledAt;
            closedAt = now;
        } else if ("REOPENED".equals(update.actionType())) {
            handledAt = null;
            resolvedAt = null;
            closedAt = null;
        }
        List<FeedbackActionItem> actions = new ArrayList<>(current.actions());
        actions.add(new FeedbackActionItem(
                actionIds.incrementAndGet(), update.actionType(), item.status(), update.status(), update.handlerId(),
                employeeName(update.handlerId()), update.content() == null ? update.handleResult() : update.content(),
                now, update.operatorId(), "本地管理员"));
        FeedbackSummary summary = new FeedbackSummary(
                item.id(), item.feedbackNo(), item.visitTaskId(), item.visitRecordId(), item.memberId(),
                item.memberName(), item.maskedMobile(), item.billId(), item.billNo(), item.storeId(), item.storeName(),
                item.channel(), item.score(), item.content(), item.complaintType(), update.status(), update.handlerId(),
                employeeName(update.handlerId()), update.handleResult(), handledAt, resolvedAt, closedAt,
                update.dueHours() == null ? item.dueHours() : update.dueHours(),
                update.dueAt() == null ? item.dueAt() : update.dueAt(), false, 0,
                actions.size(), item.createdAt(), now);
        FeedbackDetail updated = new FeedbackDetail(summary, actions);
        feedback.removeIf(entry -> entry.feedback().id() == update.id());
        feedback.add(updated);
        return updated;
    }

    private String employeeName(Long id) {
        if (id == null) return null;
        return id == 101L ? "安然" : id == 102L ? "若溪" : "员工" + id;
    }

    private FeedbackSummary withTiming(FeedbackSummary item) {
        LocalDateTime now = LocalDateTime.now();
        boolean overdue = ("OPEN".equals(item.status()) || "PROCESSING".equals(item.status()))
                && item.dueAt().isBefore(now);
        long overdueMinutes = overdue ? Math.max(0, Duration.between(item.dueAt(), now).toMinutes()) : 0;
        return new FeedbackSummary(
                item.id(), item.feedbackNo(), item.visitTaskId(), item.visitRecordId(), item.memberId(),
                item.memberName(), item.maskedMobile(), item.billId(), item.billNo(), item.storeId(), item.storeName(),
                item.channel(), item.score(), item.content(), item.complaintType(), item.status(), item.handlerId(),
                item.handlerName(), item.handleResult(), item.handledAt(), item.resolvedAt(), item.closedAt(),
                item.dueHours(), item.dueAt(), overdue, overdueMinutes,
                item.actionCount(), item.createdAt(), item.updatedAt());
    }
}
