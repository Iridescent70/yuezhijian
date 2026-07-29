package com.yuezhijian.server.job;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.file.FileObjectService;
import com.yuezhijian.server.file.FileStorageProperties;
import com.yuezhijian.server.file.MemoryFileObjectRepository;
import com.yuezhijian.server.file.MemoryObjectStorage;
import com.yuezhijian.server.iam.MemoryAccessCatalogService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsyncJobServiceTest {
    @Test
    void oneUserCannotAccumulateMoreThanThreeActiveJobs() {
        AsyncJobHandler handler = new AsyncJobHandler() {
            @Override public String jobType() { return ServiceFeedbackCsvJobHandler.JOB_TYPE; }

            @Override
            public AsyncJobExecutionResult execute(AsyncJobTask task) {
                return new AsyncJobExecutionResult(
                        "result.csv", "text/csv", "\ufeff\"ok\"".getBytes(StandardCharsets.UTF_8), 1, 0);
            }
        };
        FileObjectService files = new FileObjectService(
                new MemoryFileObjectRepository(), new MemoryObjectStorage(),
                new FileStorageProperties(1024, 10, ".data/test-uploads", null, null, null, "test"));
        AsyncJobService service = new AsyncJobService(
                new MemoryAsyncJobRepository(), new MemoryAccessCatalogService(), files,
                new ObjectMapper(), new AsyncJobNumberGenerator(), List.of(handler));
        CreateExportRequest request = new CreateExportRequest("SERVICE_FEEDBACK", null, null, null);
        for (int index = 0; index < 3; index++) service.createExport(request, "admin");

        assertThatThrownBy(() -> service.createExport(request, "admin"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("最多同时保留3个");
    }
}
