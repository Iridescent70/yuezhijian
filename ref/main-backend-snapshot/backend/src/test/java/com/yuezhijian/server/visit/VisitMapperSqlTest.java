package com.yuezhijian.server.visit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class VisitMapperSqlTest {
    @Test
    void taskQueryCanParseOverdueEmployeeAndKeywordFilters() throws Exception {
        Method method = VisitMapper.class.getMethod("findTasks", VisitTaskQuery.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        VisitTaskQuery query = new VisitTaskQuery(2L, 101L, "OVERDUE", LocalDate.now(), "会员");

        BoundSql sql = source.getBoundSql(Map.of("query", query));

        assertThat(sql.getSql()).contains(
                "task.status = 'PENDING' AND task.due_at < sysdatetime()",
                "fp.employee_id = ?",
                "task.task_no LIKE ?");
        assertThat(sql.getParameterMappings()).isNotEmpty();
    }

    @Test
    void taskCompletionAndCancellationOnlyChangeAllowedStates() throws Exception {
        Method complete = VisitMapper.class.getMethod("autoCompleteTask", long.class, long.class);
        Method cancel = VisitMapper.class.getMethod(
                "cancelPendingByBill", long.class, String.class, long.class);

        String completeSql = String.join(" ", complete.getAnnotation(Update.class).value());
        String cancelSql = String.join(" ", cancel.getAnnotation(Update.class).value());

        assertThat(completeSql).contains("task.status = 'PENDING'", "participant.status = 'PENDING'");
        assertThat(cancelSql).contains("status = 'CANCELLED'", "WHERE bill_id = #{billId} AND status = 'PENDING'");
    }
}
