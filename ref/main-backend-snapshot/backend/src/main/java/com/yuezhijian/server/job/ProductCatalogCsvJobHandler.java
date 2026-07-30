package com.yuezhijian.server.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.product.ProductRepository;
import com.yuezhijian.server.product.ProductSummary;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalogCsvJobHandler implements AsyncJobHandler {
    public static final String JOB_TYPE = "PRODUCT_CATALOG_EXPORT";
    private static final int MAX_ROWS = 50_000;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final ProductRepository repository;
    private final ObjectMapper objectMapper;

    public ProductCatalogCsvJobHandler(ProductRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public AsyncJobExecutionResult execute(AsyncJobTask task) {
        ProductCatalogExportRequest request = request(task.requestJson());
        List<ProductSummary> products = repository.products(task.storeId(), null, null, request.keyword());
        if (products.size() > MAX_ROWS) throw new IllegalArgumentException("单次产品导出不能超过50000条");
        StringBuilder csv = new StringBuilder("\ufeff");
        line(csv, "产品编号", "产品名称", "产品分类", "计量单位", "条码", "成本", "标准售价",
                "门店售价", "库存跟踪", "销售状态", "资料状态");
        products.forEach(product -> line(
                csv, product.code(), product.name(), product.categoryName(), product.unitName(), product.barcode(),
                product.costPrice().toPlainString(), product.salePrice().toPlainString(),
                product.storePrice().toPlainString(), product.trackStock() ? "是" : "否",
                "ON_SALE".equals(product.saleStatus()) ? "在售" : "未上架",
                "ACTIVE".equals(product.status()) ? "启用" : "停用"));
        return new AsyncJobExecutionResult(
                "产品资料-" + LocalDateTime.now().format(FILE_TIME) + ".csv",
                "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8), products.size(), 0);
    }

    private ProductCatalogExportRequest request(String json) {
        try {
            return objectMapper.readValue(json, ProductCatalogExportRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("产品导出任务参数无法解析", exception);
        }
    }

    private static void line(StringBuilder csv, Object... cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) csv.append(',');
            csv.append(CsvValues.cell(cells[index]));
        }
        csv.append("\r\n");
    }
}
