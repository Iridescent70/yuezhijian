package com.yuezhijian.server.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.feedback.FeedbackQuery;
import com.yuezhijian.server.feedback.FeedbackRepository;
import com.yuezhijian.server.feedback.FeedbackSummary;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ServiceFeedbackCsvJobHandler implements AsyncJobHandler {
    public static final String JOB_TYPE = "SERVICE_FEEDBACK_EXPORT";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter CELL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper;

    public ServiceFeedbackCsvJobHandler(FeedbackRepository feedbackRepository, ObjectMapper objectMapper) {
        this.feedbackRepository = feedbackRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public AsyncJobExecutionResult execute(AsyncJobTask task) {
        ServiceFeedbackExportRequest request = request(task.requestJson());
        List<FeedbackSummary> rows = feedbackRepository.feedback(new FeedbackQuery(
                task.storeId(), null, null, request.status(), request.overdue(), null));
        StringBuilder csv = new StringBuilder("\ufeff");
        line(csv, "反馈编号", "门店", "会员", "手机号", "账单编号", "评分", "状态", "负责人",
                "是否超时", "处理时限", "反馈内容", "处理结果", "创建时间");
        for (FeedbackSummary row : rows) {
            line(csv, row.feedbackNo(), row.storeName(), row.memberName(), row.maskedMobile(), row.billNo(),
                    row.score(), row.status(), row.handlerName(), row.overdue() ? "是" : "否", time(row.dueAt()),
                    row.content(), row.handleResult(), time(row.createdAt()));
        }
        return new AsyncJobExecutionResult(
                "服务反馈-" + LocalDateTime.now().format(FILE_TIME) + ".csv",
                "text/csv",
                csv.toString().getBytes(StandardCharsets.UTF_8),
                rows.size(),
                0);
    }

    private ServiceFeedbackExportRequest request(String requestJson) {
        try {
            return objectMapper.readValue(requestJson, ServiceFeedbackExportRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("导出任务参数无法解析", exception);
        }
    }

    private static void line(StringBuilder csv, Object... cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) csv.append(',');
            csv.append(CsvValues.cell(cells[index]));
        }
        csv.append("\r\n");
    }

    private static String time(LocalDateTime value) {
        return value == null ? "" : value.format(CELL_TIME);
    }
}
