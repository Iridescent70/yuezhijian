package com.yuezhijian.server.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
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

    private BoundSql parse(Method method, Map<String, Object> parameters) {
        Select select = method.getAnnotation(Select.class);
        SqlSource sqlSource = new XMLLanguageDriver().createSqlSource(
                new Configuration(), String.join(" ", select.value()), Map.class);
        return sqlSource.getBoundSql(new HashMap<>(parameters));
    }
}
