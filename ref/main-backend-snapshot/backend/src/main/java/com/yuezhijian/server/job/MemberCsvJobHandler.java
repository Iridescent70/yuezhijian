package com.yuezhijian.server.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.member.MemberQuery;
import com.yuezhijian.server.member.MemberRepository;
import com.yuezhijian.server.member.MemberSummary;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class MemberCsvJobHandler implements AsyncJobHandler {
    public static final String JOB_TYPE = "MEMBER_EXPORT";
    private static final int PAGE_SIZE = 100;
    private static final long MAX_ROWS = 50_000;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter CELL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    public MemberCsvJobHandler(MemberRepository memberRepository, ObjectMapper objectMapper) {
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public AsyncJobExecutionResult execute(AsyncJobTask task) {
        MemberExportRequest request = request(task.requestJson());
        StringBuilder csv = new StringBuilder("\ufeff");
        line(csv, "会员编号", "姓名", "手机号", "性别", "会员等级", "归属门店", "储值余额",
                "可用积分", "有效次卡", "会员状态", "最近到店");
        int exported = 0;
        int page = 1;
        while (true) {
            PageResult<MemberSummary> result = memberRepository.search(new MemberQuery(
                    request.keyword(), task.storeId(), request.status(), page, PAGE_SIZE, null));
            if (result.total() > MAX_ROWS) throw new IllegalArgumentException("单次会员导出不能超过50000条");
            for (MemberSummary member : result.items()) {
                line(csv, member.memberNo(), member.fullName(), member.maskedMobile(), gender(member.gender()),
                        member.levelName(), member.ownerStoreName(), member.availableBalance().toPlainString(),
                        member.availablePoints(), member.cardCount(), status(member.status()), time(member.lastVisitAt()));
                exported++;
            }
            if ((long) page * PAGE_SIZE >= result.total()) break;
            page++;
        }
        return new AsyncJobExecutionResult(
                "会员名单-" + LocalDateTime.now().format(FILE_TIME) + ".csv",
                "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8), exported, 0);
    }

    private MemberExportRequest request(String requestJson) {
        try {
            return objectMapper.readValue(requestJson, MemberExportRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("会员导出任务参数无法解析", exception);
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

    private static String gender(String value) {
        return switch (value) {
            case "FEMALE" -> "女";
            case "MALE" -> "男";
            case "OTHER" -> "其他";
            default -> "未知";
        };
    }

    private static String status(String value) {
        return switch (value) {
            case "ACTIVE" -> "正常";
            case "FROZEN" -> "已冻结";
            case "INACTIVE" -> "已停用";
            default -> value;
        };
    }
}
