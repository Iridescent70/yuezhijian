package com.yuezhijian.server.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.file.FileObjectItem;
import com.yuezhijian.server.file.FileObjectService;
import com.yuezhijian.server.file.FileStorageProperties;
import com.yuezhijian.server.file.MemoryFileObjectRepository;
import com.yuezhijian.server.file.MemoryObjectStorage;
import com.yuezhijian.server.iam.MemoryAccessCatalogService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsyncJobCleanupTest {
    @Test
    void expiredResultIsPhysicallyDeletedAndTheJobAuditRecordIsRetained() {
        MemoryAsyncJobRepository jobs = new MemoryAsyncJobRepository();
        MemoryObjectStorage storage = spy(new MemoryObjectStorage());
        FileObjectService files = new FileObjectService(
                new MemoryFileObjectRepository(), storage,
                new FileStorageProperties(1024, 10, ".data/test-uploads", null, null, null, "test"));
        FileObjectItem file = files.storeGenerated(
                "ASYNC_JOB_RESULT", "expired.csv", "text/csv", "result".getBytes(StandardCharsets.UTF_8), 1);
        AsyncJobItem created = jobs.create(new AsyncJobDraft(
                "JOB-CLEAN-1", "到期清理测试", "TEST", "{}", 1,
                LocalDateTime.now().minusSeconds(1), 1));
        AsyncJobTask task = jobs.claimNext("worker-1", LocalDateTime.now().plusMinutes(30), 3).orElseThrow();
        jobs.complete(task.id(), task.leaseToken(), file, 1, 0);
        AsyncJobService service = new AsyncJobService(
                jobs, new MemoryAccessCatalogService(), files, new ObjectMapper(),
                new AsyncJobNumberGenerator(), new AsyncJobProperties(30, 3), List.of());
        try {
            assertThat(service.cleanupExpiredResults()).isEqualTo(1);
            assertThat(service.cleanupExpiredResults()).isZero();
            assertThat(jobs.findOwned(created.id(), 1)).isPresent();
            verify(storage).delete(anyString());
            assertThatThrownBy(() -> files.downloadGenerated(file.id()))
                    .isInstanceOf(ResourceNotFoundException.class);
        } finally {
            service.shutdownLeaseHeartbeat();
        }
    }
}
