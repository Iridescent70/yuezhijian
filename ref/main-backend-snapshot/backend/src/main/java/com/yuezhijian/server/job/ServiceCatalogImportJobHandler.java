package com.yuezhijian.server.job;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.file.FileObjectService;
import com.yuezhijian.server.masterdata.MasterDataService;
import com.yuezhijian.server.masterdata.ServiceImportOutcome;
import com.yuezhijian.server.masterdata.ServiceImportRow;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ServiceCatalogImportJobHandler implements AsyncJobHandler {
    public static final String JOB_TYPE = "SERVICE_CATALOG_IMPORT";
    private static final int MAX_FILE_ROWS = 5_001;
    private static final List<String> HEADERS = List.of(
            "项目编号", "项目名称", "分类编号", "时长(分钟)", "成本", "标准售价", "门店售价", "项目说明");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final FileObjectService files;
    private final MasterDataService masterData;

    public ServiceCatalogImportJobHandler(FileObjectService files, MasterDataService masterData) {
        this.files = files;
        this.masterData = masterData;
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public AsyncJobExecutionResult execute(AsyncJobTask task) {
        if (task.inputFileId() == null) throw new IllegalArgumentException("服务项目导入缺少输入文件");
        List<List<String>> rows = CsvTableParser.parse(
                files.downloadJobInput(task.inputFileId()).content(), MAX_FILE_ROWS);
        if (rows.isEmpty()) throw new IllegalArgumentException("导入文件没有表头和数据");
        if (!HEADERS.equals(rows.getFirst())) {
            throw new IllegalArgumentException("CSV表头不正确，请重新下载导入模板");
        }
        if (rows.size() == 1) throw new IllegalArgumentException("导入文件没有数据行");

        StringBuilder result = new StringBuilder("\ufeff");
        line(result, "行号", "项目编号", "处理结果", "服务ID", "说明");
        int success = 0;
        int failure = 0;
        for (int index = 1; index < rows.size(); index++) {
            List<String> cells = rows.get(index);
            String code = cells.isEmpty() ? "" : cells.getFirst().trim();
            try {
                if (cells.size() != HEADERS.size()) throw new IllegalArgumentException("列数必须为8列");
                ServiceImportOutcome outcome = masterData.importService(toImportRow(cells), task.storeId(), task.createdBy());
                line(result, index + 1, code, "成功", outcome.serviceId(), outcome.message());
                success++;
            } catch (RuntimeException exception) {
                line(result, index + 1, code, "失败", "", safeMessage(exception));
                failure++;
            }
        }
        return new AsyncJobExecutionResult(
                "服务项目导入结果-" + LocalDateTime.now().format(FILE_TIME) + ".csv",
                "text/csv", result.toString().getBytes(StandardCharsets.UTF_8), success, failure);
    }

    private ServiceImportRow toImportRow(List<String> cells) {
        return new ServiceImportRow(
                cells.get(0), cells.get(1), cells.get(2), integer(cells.get(3), "时长"),
                amount(cells.get(4), "成本"), amount(cells.get(5), "标准售价"),
                amount(cells.get(6), "门店售价"), cells.get(7));
    }

    private static int integer(String value, String field) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + "必须是整数");
        }
    }

    private static BigDecimal amount(String value, String field) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + "必须是有效金额");
        }
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
