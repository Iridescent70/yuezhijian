package com.yuezhijian.server.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ReversalMapperSqlTest {
    @Test
    void searchCanParseOptionalStatusFilter() throws Exception {
        Method method = ReversalMapper.class.getMethod("search", String.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);

        BoundSql sql = source.getBoundSql(Map.of("status", "APPROVED"));

        assertThat(sql.getSql()).contains("reversal.status = ?", "requested_at DESC");
        assertThat(sql.getParameterMappings()).hasSize(1);
    }

    @Test
    void executionRequiresApprovedVersionAndSettledBill() throws Exception {
        Method execute = ReversalMapper.class.getMethod(
                "markExecuted", long.class, String.class, String.class, long.class);
        Method bill = ReversalMapper.class.getMethod("reverseBill", long.class, long.class);

        String executeSql = String.join(" ", execute.getAnnotation(Update.class).value());
        String billSql = String.join(" ", bill.getAnnotation(Update.class).value());

        assertThat(executeSql).contains("status = 'APPROVED'", "row_version = CONVERT(binary(8), #{version}, 1)");
        assertThat(billSql).contains("status = 'REVERSED'", "status = 'SETTLED'");
    }
}
