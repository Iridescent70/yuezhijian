package com.yuezhijian.server.job;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.file.FileObjectItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerAsyncJobRepository implements AsyncJobRepository {
    private final AsyncJobMapper mapper;

    public SqlServerAsyncJobRepository(AsyncJobMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int countActive(long createdBy) {
        return mapper.countActive(createdBy);
    }

    @Override
    public AsyncJobItem create(AsyncJobDraft draft) {
        long id = mapper.insert(draft);
        return findOwned(id, draft.operatorId()).orElseThrow();
    }

    @Override
    public PageResult<AsyncJobItem> jobs(AsyncJobQuery query) {
        return new PageResult<>(mapper.findJobs(query), query.page(), query.size(), mapper.countJobs(query));
    }

    @Override
    public Optional<AsyncJobItem> findOwned(long id, long createdBy) {
        return Optional.ofNullable(mapper.findOwned(id, createdBy));
    }

    @Override
    public Optional<AsyncJobTask> claimNext(String leaseToken, LocalDateTime leaseExpiresAt, int maxAttempts) {
        return Optional.ofNullable(mapper.claimNext(leaseToken, leaseExpiresAt, maxAttempts));
    }

    @Override
    public boolean renewLease(long id, String leaseToken, LocalDateTime leaseExpiresAt) {
        return mapper.renewLease(id, leaseToken, leaseExpiresAt) == 1;
    }

    @Override
    public void complete(
            long id, String leaseToken, FileObjectItem resultFile, int successCount, int failureCount) {
        String status = failureCount == 0 ? "SUCCEEDED" : "PARTIAL";
        if (mapper.complete(id, leaseToken, status, successCount, failureCount, resultFile.id()) != 1) {
            throw new DuplicateResourceException("任务状态已发生变化");
        }
    }

    @Override
    public void fail(long id, String leaseToken, String errorMessage) {
        mapper.fail(id, leaseToken, errorMessage);
    }

    @Override
    public int failExhausted(int maxAttempts) {
        return mapper.failExhausted(maxAttempts);
    }

    @Override
    public List<ExpiredJobResult> expiredResults(int limit) {
        return mapper.findExpiredResults(limit);
    }

    @Override
    public void markResultPurged(long jobId, long fileId) {
        mapper.markResultPurged(jobId, fileId);
    }

    @Override
    public boolean cancel(long id, long createdBy) {
        return mapper.cancel(id, createdBy) == 1;
    }
}
