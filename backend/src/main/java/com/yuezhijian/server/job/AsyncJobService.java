package com.yuezhijian.server.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.file.FileObjectItem;
import com.yuezhijian.server.file.FileObjectService;
import com.yuezhijian.server.file.FileStorageException;
import com.yuezhijian.server.file.StoredFileDownload;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.UserIdentity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AsyncJobService {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncJobService.class);
    private static final Set<String> STATUSES = Set.of(
            "PENDING", "RUNNING", "SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED");
    private static final Set<String> FEEDBACK_STATUSES = Set.of("OPEN", "PROCESSING", "RESOLVED", "CLOSED");

    private final AsyncJobRepository repository;
    private final AccessCatalogService accessCatalog;
    private final FileObjectService files;
    private final ObjectMapper objectMapper;
    private final AsyncJobNumberGenerator numbers;
    private final Map<String, AsyncJobHandler> handlers;

    public AsyncJobService(
            AsyncJobRepository repository,
            AccessCatalogService accessCatalog,
            FileObjectService files,
            ObjectMapper objectMapper,
            AsyncJobNumberGenerator numbers,
            List<AsyncJobHandler> handlers) {
        this.repository = repository;
        this.accessCatalog = accessCatalog;
        this.files = files;
        this.objectMapper = objectMapper;
        this.numbers = numbers;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                AsyncJobHandler::jobType, Function.identity()));
    }

    public AsyncJobItem createExport(CreateExportRequest request, String username) {
        String exportType = normalize(request.exportType());
        if (!"SERVICE_FEEDBACK".equals(exportType)) throw new IllegalArgumentException("暂不支持该导出类型");
        String status = optionalStatus(request.status(), FEEDBACK_STATUSES, "服务反馈状态无效");
        UserIdentity operator = accessCatalog.userIdentity(username);
        if (repository.countActive(operator.id()) >= 3) {
            throw new DuplicateResourceException("最多同时保留3个等待中或执行中的任务");
        }
        String requestJson = json(new ServiceFeedbackExportRequest(status, request.overdue()));
        return repository.create(new AsyncJobDraft(
                numbers.next(), "服务反馈导出", ServiceFeedbackCsvJobHandler.JOB_TYPE,
                requestJson, operator.currentStoreId(), LocalDateTime.now().plusDays(7), operator.id()));
    }

    public PageResult<AsyncJobItem> jobs(
            String jobType, String status, int page, int size, String username) {
        if (page < 1) throw new IllegalArgumentException("页码必须从1开始");
        if (size < 1 || size > 100) throw new IllegalArgumentException("每页数量必须在1到100之间");
        String normalizedType = optional(jobType);
        if (normalizedType != null && !handlers.containsKey(normalizedType)) {
            throw new IllegalArgumentException("任务类型无效");
        }
        String normalizedStatus = optionalStatus(status, STATUSES, "任务状态无效");
        long userId = accessCatalog.userIdentity(username).id();
        return repository.jobs(new AsyncJobQuery(userId, normalizedType, normalizedStatus, page, size));
    }

    public AsyncJobItem detail(long id, String username) {
        return owned(id, username);
    }

    public AsyncJobItem cancel(long id, String username) {
        UserIdentity operator = accessCatalog.userIdentity(username);
        AsyncJobItem current = repository.findOwned(id, operator.id())
                .orElseThrow(() -> new ResourceNotFoundException("任务不存在"));
        if (!"PENDING".equals(current.status())) throw new DuplicateResourceException("只有等待中的任务可以取消");
        if (!repository.cancel(id, operator.id())) throw new DuplicateResourceException("任务状态已发生变化");
        return repository.findOwned(id, operator.id()).orElseThrow();
    }

    public StoredFileDownload downloadResult(long id, String username) {
        AsyncJobItem job = owned(id, username);
        if (!("SUCCEEDED".equals(job.status()) || "PARTIAL".equals(job.status()))
                || job.resultFileId() == null) {
            throw new DuplicateResourceException("任务结果尚不可下载");
        }
        if (job.expiresAt() != null && !job.expiresAt().isAfter(LocalDateTime.now())) {
            throw new ResourceNotFoundException("任务结果已过期");
        }
        return files.downloadGenerated(job.resultFileId());
    }

    public boolean processNext() {
        return repository.claimNext().map(task -> {
            try {
                AsyncJobHandler handler = handlers.get(task.jobType());
                if (handler == null) throw new IllegalArgumentException("没有可执行的任务处理器");
                AsyncJobExecutionResult result = handler.execute(task);
                FileObjectItem resultFile = files.storeGenerated(
                        "ASYNC_JOB_RESULT", result.fileName(), result.contentType(), result.content(), task.createdBy());
                repository.complete(task.id(), resultFile, result.successCount(), result.failureCount());
            } catch (RuntimeException exception) {
                String message = safeError(exception);
                repository.fail(task.id(), message);
                LOG.error("Async job {} failed: {}", task.jobNo(), message, exception);
            }
            return true;
        }).orElse(false);
    }

    private AsyncJobItem owned(long id, String username) {
        long userId = accessCatalog.userIdentity(username).id();
        return repository.findOwned(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("任务不存在"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("任务参数序列化失败", exception);
        }
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : normalize(value);
    }

    private static String optionalStatus(String value, Set<String> allowed, String message) {
        String normalized = optional(value);
        if (normalized != null && !allowed.contains(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static String safeError(RuntimeException exception) {
        String message = exception instanceof IllegalArgumentException
                        || exception instanceof DuplicateResourceException
                        || exception instanceof ResourceNotFoundException
                        || exception instanceof FileStorageException
                ? exception.getMessage() : "任务执行失败，请联系管理员并提供任务编号";
        if (message == null || message.isBlank()) message = "任务执行失败";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
