package com.yuezhijian.server.job;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.file.FileObjectService;
import com.yuezhijian.server.product.ProductImportOutcome;
import com.yuezhijian.server.product.ProductImportRow;
import com.yuezhijian.server.product.ProductService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalogImportJobHandler implements AsyncJobHandler {
    public static final String JOB_TYPE = "PRODUCT_CATALOG_IMPORT";
    private static final int MAX_FILE_ROWS = 5_001;
    private static final List<String> HEADERS = List.of(
            "产品编号", "产品名称", "分类编号", "单位编号", "条码",
            "成本", "标准售价", "门店售价", "跟踪库存", "产品说明");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final FileObjectService files;
    private final ProductService products;

    public ProductCatalogImportJobHandler(FileObjectService files, ProductService products) {
        this.files = files;
        this.products = products;
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public AsyncJobExecutionResult execute(AsyncJobTask task) {
        if (task.inputFileId() == null) throw new IllegalArgumentException("产品资料导入缺少输入文件");
        List<List<String>> rows = CsvTableParser.parse(
                files.downloadJobInput(task.inputFileId()).content(), MAX_FILE_ROWS);
        if (rows.isEmpty()) throw new IllegalArgumentException("导入文件没有表头和数据");
        if (!HEADERS.equals(rows.getFirst())) {
            throw new IllegalArgumentException("CSV表头不正确，请重新下载导入模板");
        }
        if (rows.size() == 1) throw new IllegalArgumentException("导入文件没有数据行");

        StringBuilder result = new StringBuilder("\ufeff");
        line(result, "行号", "产品编号", "处理结果", "产品ID", "说明");
        int success = 0;
        int failure = 0;
        for (int index = 1; index < rows.size(); index++) {
            List<String> cells = rows.get(index);
            String code = cells.isEmpty() ? "" : cells.getFirst().trim();
            try {
                if (cells.size() != HEADERS.size()) throw new IllegalArgumentException("列数必须为10列");
                ProductImportOutcome outcome = products.importProduct(
                        toImportRow(cells), task.storeId(), task.createdBy());
                line(result, index + 1, code, "成功", outcome.productId(), outcome.message());
                success++;
            } catch (RuntimeException exception) {
                line(result, index + 1, code, "失败", "", safeMessage(exception));
                failure++;
            }
        }
        return new AsyncJobExecutionResult(
                "产品资料导入结果-" + LocalDateTime.now().format(FILE_TIME) + ".csv",
                "text/csv", result.toString().getBytes(StandardCharsets.UTF_8), success, failure);
    }

    private ProductImportRow toImportRow(List<String> cells) {
        return new ProductImportRow(
                cells.get(0), cells.get(1), cells.get(2), cells.get(3), cells.get(4),
                amount(cells.get(5), "成本"), amount(cells.get(6), "标准售价"),
                amount(cells.get(7), "门店售价"), bool(cells.get(8)), cells.get(9));
    }

    private static BigDecimal amount(String value, String field) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + "必须是有效金额");
        }
    }

    private static boolean bool(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "是", "TRUE", "Y", "YES", "1" -> true;
            case "否", "FALSE", "N", "NO", "0" -> false;
            default -> throw new IllegalArgumentException("跟踪库存只能填写是或否");
        };
    }

    private static String safeMessage(RuntimeException exception) {
        if (!(exception instanceof IllegalArgumentException)
                && !(exception instanceof DuplicateResourceException)) {
            return "处理失败，请联系管理员并提供任务编号";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "处理失败";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private static void line(StringBuilder csv, Object... cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) csv.append(',');
            csv.append(CsvValues.cell(cells[index]));
        }
        csv.append("\r\n");
    }
}
