package com.yuezhijian.server.feedback;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerFeedbackRepository implements FeedbackRepository {
    private final FeedbackMapper mapper;

    public SqlServerFeedbackRepository(FeedbackMapper mapper) { this.mapper = mapper; }

    @Override
    public List<FeedbackSummary> feedback(FeedbackQuery query) { return mapper.findFeedback(query); }

    @Override
    public Optional<FeedbackDetail> findById(long id) {
        return Optional.ofNullable(mapper.findSummary(id)).map(this::detail);
    }

    @Override
    public Optional<FeedbackDetail> findByVisitRecordId(long visitRecordId) {
        return Optional.ofNullable(mapper.findByVisitRecordId(visitRecordId)).map(this::detail);
    }

    @Override
    public FeedbackDetail create(FeedbackDraft draft) {
        Optional<FeedbackDetail> existing = findByVisitRecordId(draft.visitRecordId());
        if (existing.isPresent()) return existing.get();
        mapper.insertFeedback(draft);
        FeedbackSummary created = Optional.ofNullable(mapper.findByVisitRecordId(draft.visitRecordId()))
                .orElseThrow();
        mapper.insertCreatedAction(created.id(), draft.createdAt(), draft.createdBy());
        return findById(created.id()).orElseThrow();
    }

    @Override
    public FeedbackDetail update(FeedbackUpdate update) {
        if (mapper.updateFeedback(update) != 1) {
            throw new DuplicateResourceException("服务反馈已被他人处理，请刷新后重试");
        }
        mapper.insertAction(update);
        return findById(update.id()).orElseThrow();
    }

    private FeedbackDetail detail(FeedbackSummary summary) {
        return new FeedbackDetail(summary, mapper.findActions(summary.id()), List.of());
    }
}
