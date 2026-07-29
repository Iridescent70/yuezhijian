package com.yuezhijian.server.job;

import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.file.FileObjectItem;
import java.util.Optional;

public interface AsyncJobRepository {
    int countActive(long createdBy);

    AsyncJobItem create(AsyncJobDraft draft);

    PageResult<AsyncJobItem> jobs(AsyncJobQuery query);

    Optional<AsyncJobItem> findOwned(long id, long createdBy);

    Optional<AsyncJobTask> claimNext();

    void complete(long id, FileObjectItem resultFile, int successCount, int failureCount);

    void fail(long id, String errorMessage);

    boolean cancel(long id, long createdBy);
}
