package com.yuezhijian.server.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AppointmentMapperSqlTest {
    @Test
    void appointmentSearchAndConflictQueriesCanBeParsed() throws Exception {
        AppointmentQuery query = new AppointmentQuery(
                2L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7), "CONFIRMED");
        BoundSql searchSql = parse(
                AppointmentMapper.class.getMethod("search", AppointmentQuery.class, LocalDateTime.class, LocalDateTime.class),
                Map.of("query", query, "from", query.startDate().atStartOfDay(),
                        "until", query.endDate().plusDays(1).atStartOfDay()));
        assertThat(searchSql.getSql()).contains("a.status = ?");
        assertThat(searchSql.getSql()).contains("a.start_at >= ?");

        BoundSql conflictSql = parse(
                AppointmentMapper.class.getMethod(
                        "countConflicts", long.class, long.class, Long.class,
                        LocalDateTime.class, LocalDateTime.class, Long.class),
                Map.of("storeId", 2L, "employeeId", 101L, "workstationId", 201L,
                        "startAt", LocalDateTime.of(2026, 8, 1, 10, 0),
                        "endAt", LocalDateTime.of(2026, 8, 1, 11, 0), "excludeAppointmentId", 99L));
        assertThat(conflictSql.getSql()).contains("a.workstation_id = ?");
        assertThat(conflictSql.getSql()).contains("a.id != ?");
    }

    @Test
    void cancellationTransitionRequiresAnActiveConfiguredReason() throws Exception {
        Method transition = AppointmentMapper.class.getMethod(
                "transition", AppointmentStatusChange.class);
        String sql = String.join(" ", transition.getAnnotation(Update.class).value());

        assertThat(sql).contains(
                "business_type = 'APPOINTMENT'",
                "reason_code = #{reasonCode}",
                "status = 'ACTIVE'");
    }

    private BoundSql parse(Method method, Map<String, Object> parameters) {
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        return source.getBoundSql(new HashMap<>(parameters));
    }
}
