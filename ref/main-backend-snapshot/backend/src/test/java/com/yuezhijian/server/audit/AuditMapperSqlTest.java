package com.yuezhijian.server.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AuditMapperSqlTest {
    @Test
    void insertAndStoreScopedHistorySqlCanBeParsed() throws Exception {
        Insert insert = AuditMapper.class.getMethod("insert", NewAuditEvent.class).getAnnotation(Insert.class);
        assertThat(String.join(" ", insert.value()))
                .contains("INSERT INTO dbo.sys_audit_log")
                .contains("before_json, after_json")
                .contains("'SUCCESS'");

        BoundSql history = parse(
                AuditMapper.class.getMethod("findHistory", String.class, String.class, List.class),
                Map.of("objectType", "PRODUCT", "objectId", "401", "accessibleStoreIds", List.of(2L)));
        assertThat(history.getSql())
                .contains("audit.object_type = ?")
                .contains("audit.store_id IS NULL")
                .contains("OR audit.store_id IN")
                .contains("audit.result = 'SUCCESS'");
        assertThat(history.getParameterMappings()).hasSize(3);
    }

    @Test
    void auditPageSqlSupportsAllFiltersAndStablePagination() throws Exception {
        AuditLogQuery query = new AuditLogQuery(
                7L, "管理员", "CATALOG", "UPDATE", "PRODUCT", "401", "SUCCESS",
                LocalDateTime.of(2026, 7, 29, 0, 0),
                LocalDateTime.of(2026, 7, 31, 0, 0), 2, 20);

        BoundSql page = parse(
                AuditMapper.class.getMethod("findPage", AuditLogQuery.class), Map.of("query", query));
        assertThat(page.getSql())
                .contains("audit.user_id = ?")
                .contains("COALESCE(account.full_name, account.username")
                .contains("audit.module LIKE")
                .contains("audit.action LIKE")
                .contains("audit.object_type LIKE")
                .contains("audit.object_id LIKE")
                .contains("audit.result = ?")
                .contains("audit.occurred_at >= ?")
                .contains("audit.occurred_at < ?")
                .contains("ORDER BY audit.occurred_at DESC, audit.id DESC")
                .contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        assertThat(page.getParameterMappings()).hasSize(11);

        BoundSql count = parse(
                AuditMapper.class.getMethod("count", AuditLogQuery.class), Map.of("query", query));
        assertThat(count.getSql()).contains("SELECT COUNT(1)").doesNotContain("OFFSET");
        assertThat(count.getParameterMappings()).hasSize(9);
    }

    private BoundSql parse(Method method, Map<String, Object> parameters) {
        Select select = method.getAnnotation(Select.class);
        SqlSource sqlSource = new XMLLanguageDriver().createSqlSource(
                new Configuration(), String.join(" ", select.value()), Map.class);
        return sqlSource.getBoundSql(new HashMap<>(parameters));
    }
}
