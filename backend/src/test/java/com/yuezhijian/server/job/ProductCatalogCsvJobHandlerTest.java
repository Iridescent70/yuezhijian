package com.yuezhijian.server.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.product.MemoryProductRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProductCatalogCsvJobHandlerTest {
    @Test
    void exportUsesTheTaskStoreAndKeyword() {
        ProductCatalogCsvJobHandler handler = new ProductCatalogCsvJobHandler(
                new MemoryProductRepository(), new ObjectMapper());
        AsyncJobExecutionResult result = handler.execute(new AsyncJobTask(
                1, "JOB-PRODUCT-1", ProductCatalogCsvJobHandler.JOB_TYPE,
                "{\"keyword\":\"690000000001\"}", 2, null, 1, "worker-1", 1));

        String csv = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(csv).startsWith("\ufeff").contains(
                "产品编号", "PRD001", "护甲精华油", "690000000001", "35.00", "98.00", "在售", "启用");
    }
}
