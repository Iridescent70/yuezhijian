package com.yuezhijian.server.job;

import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.file.FileObjectItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AsyncJobRepository {
    int countActive(long createdBy);

    AsyncJobItem create(AsyncJobDraft draft);

    PageResult<AsyncJobItem> jobs(AsyncJobQuery query);

    Optional<AsyncJobItem> findOwned(long id, long createdBy);

    Optional<AsyncJobTask> claimNext(String leaseToken, LocalDateTime leaseExpiresAt, int maxAttempts);

    boolean renewLease(long id, String leaseToken, LocalDateTime leaseExpiresAt);

    void complete(long id, String leaseToken, FileObjectItem resultFile, int successCount, int failureCount);

    void fail(long id, String leaseToken, String errorMessage);

    int failExhausted(int maxAttempts);

    List<ExpiredJobResult> expiredResults(int limit);

    void markResultPurged(long jobId, long fileId);

    boolean cancel(long id, long createdBy);
}
