package com.yuezhijian.server.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class TradeMapperSqlTest {
    @Test
    void billSearchQueryCanBeParsedWithAllFilters() throws Exception {
        BillQuery query = new BillQuery(
                2L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7), "SETTLED", "B2026");
        Method method = TradeMapper.class.getMethod(
                "search", BillQuery.class, LocalDateTime.class, LocalDateTime.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        parameters.put("from", query.startDate().atStartOfDay());
        parameters.put("until", query.endDate().plusDays(1).atStartOfDay());

        BoundSql sql = source.getBoundSql(parameters);

        assertThat(sql.getSql()).contains("bill.status = ?");
        assertThat(sql.getSql()).contains("bill.bill_no LIKE CONCAT('%', ?, '%')");
        assertThat(sql.getParameterMappings()).hasSizeGreaterThanOrEqualTo(5);
    }
}
