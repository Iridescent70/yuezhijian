package com.yuezhijian.server.feedback;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository {
    List<FeedbackSummary> feedback(FeedbackQuery query);

    Optional<FeedbackDetail> findById(long id);

    Optional<FeedbackDetail> findByVisitRecordId(long visitRecordId);

    FeedbackDetail create(FeedbackDraft draft);

    FeedbackDetail update(FeedbackUpdate update);
}
