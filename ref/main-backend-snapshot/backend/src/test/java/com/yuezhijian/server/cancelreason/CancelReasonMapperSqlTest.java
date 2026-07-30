package com.yuezhijian.server.cancelreason;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class CancelReasonMapperSqlTest {
    @Test
    void managementQueryCanBeParsedWithAllFilters() throws Exception {
        Method method = CancelReasonMapper.class.getMethod(
                "findAll", String.class, String.class, String.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(
                new Configuration(), script, Map.class);
        BoundSql sql = source.getBoundSql(new HashMap<>(Map.of(
                "businessType", "BILL", "keyword", "OTHER", "status", "ACTIVE")));

        assertThat(sql.getSql()).contains(
                "reason.business_type = ?",
                "reason.reason_code LIKE CONCAT('%', ?, '%')",
                "reason.reason_name LIKE CONCAT('%', ?, '%')",
                "reason.status = ?");
        assertThat(sql.getParameterMappings()).hasSize(4);
    }

    @Test
    void updateUsesOptimisticLockAndRecordsOperator() throws Exception {
        Method method = CancelReasonMapper.class.getMethod("update", CancelReasonUpdate.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertThat(sql).contains(
                "updated_by = #{operatorId}",
                "row_version = CONVERT(binary(8), #{version}, 1)");
    }
}
