package com.yuezhijian.server.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.masterdata.MemoryMasterDataRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ServiceCatalogCsvJobHandlerTest {
    @Test
    void exportUsesTheTaskStoreAndKeyword() {
        ServiceCatalogCsvJobHandler handler = new ServiceCatalogCsvJobHandler(
                new MemoryMasterDataRepository(), new ObjectMapper());
        AsyncJobExecutionResult result = handler.execute(new AsyncJobTask(
                1, "JOB-SERVICE-1", ServiceCatalogCsvJobHandler.JOB_TYPE,
                "{\"keyword\":\"SVC001\"}", 2, null, 1, "worker-1", 1));

        String csv = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(csv).startsWith("\ufeff").contains(
                "项目编号", "SVC001", "基础单色美甲", "168.00", "在售", "启用")
                .doesNotContain("SVC002");
    }
}
