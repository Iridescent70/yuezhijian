package com.yuezhijian.server.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FeedbackTimingTest {
    @Test
    void overdueIsDerivedLiveAndReopenResetsTheDueSnapshot() {
        MemoryFeedbackRepository repository = new MemoryFeedbackRepository();
        LocalDateTime createdAt = LocalDateTime.now().minusHours(30);
        FeedbackDetail created = repository.create(new FeedbackDraft(
                "FB202607290001", 1, 2, 1001, "测试会员", "*******1234",
                3, "B202607290001", 2, "悦指间测试店", 2, "需要跟进",
                createdAt, 24, createdAt.plusHours(24), 1));

        FeedbackSummary overdue = repository.findById(created.feedback().id()).orElseThrow().feedback();
        assertThat(overdue.overdue()).isTrue();
        assertThat(overdue.overdueMinutes()).isGreaterThanOrEqualTo(359);
        assertThat(repository.feedback(new FeedbackQuery(null, null, null, null, true, null)))
                .extracting(FeedbackSummary::id).containsExactly(created.feedback().id());

        FeedbackDetail resolved = repository.update(new FeedbackUpdate(
                created.feedback().id(), "OPEN", "RESOLVED", 101L, "已处理", "RESOLVED", null,
                null, null, 1));
        assertThat(resolved.feedback().overdue()).isFalse();

        LocalDateTime newDueAt = LocalDateTime.now().plusHours(48);
        FeedbackDetail reopened = repository.update(new FeedbackUpdate(
                created.feedback().id(), "RESOLVED", "PROCESSING", 101L, null, "REOPENED", "再次跟进",
                48, newDueAt, 1));
        assertThat(reopened.feedback().dueHours()).isEqualTo(48);
        assertThat(reopened.feedback().dueAt()).isEqualTo(newDueAt);
        assertThat(reopened.feedback().overdue()).isFalse();
    }
}
