package com.yuezhijian.server.job;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.file.FileObjectItem;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryAsyncJobRepository implements AsyncJobRepository {
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, Entry> entries = new LinkedHashMap<>();

    @Override
    public synchronized int countActive(long createdBy) {
        return (int) entries.values().stream()
                .filter(item -> item.draft.operatorId() == createdBy)
                .filter(item -> "PENDING".equals(item.status) || "RUNNING".equals(item.status))
                .count();
    }

    @Override
    public synchronized AsyncJobItem create(AsyncJobDraft draft) {
        long id = ids.incrementAndGet();
        Entry entry = new Entry(id, draft);
        entries.put(id, entry);
        return entry.item();
    }

    @Override
    public synchronized PageResult<AsyncJobItem> jobs(AsyncJobQuery query) {
        List<AsyncJobItem> matched = entries.values().stream()
                .filter(item -> item.draft.operatorId() == query.createdBy())
                .filter(item -> query.jobType() == null || item.draft.jobType().equals(query.jobType()))
                .filter(item -> query.status() == null || item.status.equals(query.status()))
                .sorted(Comparator.comparing((Entry item) -> item.createdAt).reversed()
                        .thenComparing(item -> item.id, Comparator.reverseOrder()))
                .map(Entry::item).toList();
        int from = Math.min(query.offset(), matched.size());
        int to = Math.min(from + query.size(), matched.size());
        return new PageResult<>(matched.subList(from, to), query.page(), query.size(), matched.size());
    }

    @Override
    public synchronized Optional<AsyncJobItem> findOwned(long id, long createdBy) {
        Entry entry = entries.get(id);
        return entry == null || entry.draft.operatorId() != createdBy ? Optional.empty() : Optional.of(entry.item());
    }

    @Override
    public synchronized Optional<AsyncJobTask> claimNext(
            String leaseToken, LocalDateTime leaseExpiresAt, int maxAttempts) {
        LocalDateTime now = LocalDateTime.now();
        return entries.values().stream()
                .filter(item -> item.attemptCount < maxAttempts)
                .filter(item -> "PENDING".equals(item.status)
                        || ("RUNNING".equals(item.status)
                        && (item.leaseExpiresAt == null || !item.leaseExpiresAt.isAfter(now))))
                .min(Comparator.comparing((Entry item) -> item.createdAt).thenComparingLong(item -> item.id))
                .map(item -> {
                    item.status = "RUNNING";
                    item.progress = 1;
                    item.startedAt = LocalDateTime.now();
                    item.finishedAt = null;
                    item.errorMessage = null;
                    item.leaseToken = leaseToken;
                    item.leaseExpiresAt = leaseExpiresAt;
                    item.attemptCount++;
                    return item.task();
                });
    }

    @Override
    public synchronized boolean renewLease(long id, String leaseToken, LocalDateTime leaseExpiresAt) {
        Entry entry = entries.get(id);
        if (!ownsLease(entry, leaseToken)) return false;
        entry.leaseExpiresAt = leaseExpiresAt;
        return true;
    }

    @Override
    public synchronized void complete(
            long id, String leaseToken, FileObjectItem resultFile, int successCount, int failureCount) {
        Entry entry = requireLease(id, leaseToken);
        entry.status = failureCount == 0 ? "SUCCEEDED" : "PARTIAL";
        entry.progress = 100;
        entry.successCount = successCount;
        entry.failureCount = failureCount;
        entry.resultFile = resultFile;
        entry.finishedAt = LocalDateTime.now();
        entry.leaseToken = null;
        entry.leaseExpiresAt = null;
    }

    @Override
    public synchronized void fail(long id, String leaseToken, String errorMessage) {
        Entry entry = entries.get(id);
        if (!ownsLease(entry, leaseToken)) return;
        entry.status = "FAILED";
        entry.progress = 100;
        entry.failureCount = Math.max(1, entry.failureCount);
        entry.errorMessage = errorMessage;
        entry.finishedAt = LocalDateTime.now();
        entry.leaseToken = null;
        entry.leaseExpiresAt = null;
    }

    @Override
    public synchronized int failExhausted(int maxAttempts) {
        LocalDateTime now = LocalDateTime.now();
        int changed = 0;
        for (Entry entry : entries.values()) {
            if ("RUNNING".equals(entry.status) && entry.attemptCount >= maxAttempts
                    && (entry.leaseExpiresAt == null || !entry.leaseExpiresAt.isAfter(now))) {
                entry.status = "FAILED";
                entry.progress = 100;
                entry.failureCount = Math.max(1, entry.failureCount);
                entry.errorMessage = "任务执行节点失联，已达到最大重试次数";
                entry.finishedAt = now;
                entry.leaseToken = null;
                entry.leaseExpiresAt = null;
                changed++;
            }
        }
        return changed;
    }

    @Override
    public synchronized List<ExpiredJobResult> expiredResults(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return entries.values().stream()
                .filter(item -> ("SUCCEEDED".equals(item.status) || "PARTIAL".equals(item.status))
                        && item.resultFile != null && item.resultPurgedAt == null
                        && !item.draft.expiresAt().isAfter(now))
                .sorted(Comparator.comparing((Entry item) -> item.draft.expiresAt()).thenComparingLong(item -> item.id))
                .limit(limit)
                .map(item -> new ExpiredJobResult(item.id, item.resultFile.id()))
                .toList();
    }

    @Override
    public synchronized void markResultPurged(long jobId, long fileId) {
        Entry entry = entries.get(jobId);
        if (entry != null && entry.resultFile != null && entry.resultFile.id() == fileId
                && entry.resultPurgedAt == null && !entry.draft.expiresAt().isAfter(LocalDateTime.now())) {
            entry.resultPurgedAt = LocalDateTime.now();
        }
    }

    @Override
    public synchronized boolean cancel(long id, long createdBy) {
        Entry entry = entries.get(id);
        if (entry == null || entry.draft.operatorId() != createdBy || !"PENDING".equals(entry.status)) return false;
        entry.status = "CANCELLED";
        entry.finishedAt = LocalDateTime.now();
        return true;
    }

    private Entry requireLease(long id, String leaseToken) {
        Entry entry = entries.get(id);
        if (!ownsLease(entry, leaseToken)) {
            throw new DuplicateResourceException("任务状态已发生变化");
        }
        return entry;
    }

    private static boolean ownsLease(Entry entry, String leaseToken) {
        return entry != null && "RUNNING".equals(entry.status) && leaseToken != null
                && leaseToken.equals(entry.leaseToken);
    }

    private static class Entry {
        private final long id;
        private final AsyncJobDraft draft;
        private final LocalDateTime createdAt = LocalDateTime.now();
        private String status = "PENDING";
        private int progress;
        private int successCount;
        private int failureCount;
        private FileObjectItem resultFile;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private String leaseToken;
        private LocalDateTime leaseExpiresAt;
        private int attemptCount;
        private LocalDateTime resultPurgedAt;

        private Entry(long id, AsyncJobDraft draft) {
            this.id = id;
            this.draft = draft;
        }

        private AsyncJobTask task() {
            return new AsyncJobTask(
                    id, draft.jobNo(), draft.jobType(), draft.requestJson(), draft.storeId(), draft.operatorId(),
                    leaseToken, attemptCount);
        }

        private AsyncJobItem item() {
            return new AsyncJobItem(
                    id, draft.jobNo(), draft.jobName(), draft.jobType(), status, progress,
                    successCount, failureCount,
                    resultFile == null ? null : resultFile.id(),
                    resultFile == null ? null : resultFile.originalName(),
                    null, null, errorMessage, startedAt, finishedAt, draft.expiresAt(), createdAt,
                    draft.operatorId(), "本地管理员");
        }
    }
}
