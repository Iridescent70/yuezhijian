package com.yuezhijian.server.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import com.yuezhijian.server.masterdata.ServiceItemSummary;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ServiceCatalogCsvJobHandler implements AsyncJobHandler {
    public static final String JOB_TYPE = "SERVICE_CATALOG_EXPORT";
    private static final int MAX_ROWS = 50_000;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final MasterDataRepository repository;
    private final ObjectMapper objectMapper;

    public ServiceCatalogCsvJobHandler(MasterDataRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public AsyncJobExecutionResult execute(AsyncJobTask task) {
        ServiceCatalogExportRequest request = request(task.requestJson());
        List<ServiceItemSummary> services = repository.services(task.storeId(), request.keyword());
        if (services.size() > MAX_ROWS) throw new IllegalArgumentException("单次服务项目导出不能超过50000条");
        StringBuilder csv = new StringBuilder("\ufeff");
        line(csv, "项目编号", "项目名称", "服务分类", "时长(分钟)", "成本", "标准售价", "门店售价", "销售状态", "资料状态");
        services.forEach(service -> line(
                csv, service.code(), service.name(), service.categoryName(), service.durationMinutes(),
                service.costAmount().toPlainString(), service.listPrice().toPlainString(),
                service.storePrice().toPlainString(), saleStatus(service.saleStatus()), status(service.status())));
        return new AsyncJobExecutionResult(
                "服务项目-" + LocalDateTime.now().format(FILE_TIME) + ".csv",
                "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8), services.size(), 0);
    }

    private ServiceCatalogExportRequest request(String requestJson) {
        try {
            return objectMapper.readValue(requestJson, ServiceCatalogExportRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("服务项目导出任务参数无法解析", exception);
        }
    }

    private static void line(StringBuilder csv, Object... cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) csv.append(',');
            csv.append(CsvValues.cell(cells[index]));
        }
        csv.append("\r\n");
    }

    private static String saleStatus(String value) {
        return "ON_SALE".equals(value) ? "在售" : "未上架";
    }

    private static String status(String value) {
        return "ACTIVE".equals(value) ? "启用" : "停用";
    }
}
