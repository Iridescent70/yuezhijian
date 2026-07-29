package com.yuezhijian.server.commission;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class CommissionMapperSqlTest {
    @Test
    void ledgerQueryCanParseOptionalDirection() throws Exception {
        Method method = CommissionMapper.class.getMethod("findLedgers", CommissionLedgerQuery.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        CommissionLedgerQuery query = new CommissionLedgerQuery(
                101L, 2L, LocalDate.now().minusDays(1), LocalDate.now(), "NEGATIVE", "CALCULATED");

        BoundSql sql = source.getBoundSql(Map.of("query", query));

        assertThat(sql.getSql()).contains("l.commission_amount < 0", "l.calculation_status = ?");
        assertThat(sql.getParameterMappings()).isNotEmpty();
    }

    @Test
    void applicablePlanPrefersPositionThenStoreSpecificity() throws Exception {
        Method method = CommissionMapper.class.getMethod(
                "findApplicablePlan", String.class, long.class, Long.class, LocalDate.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("p.position_id IS NULL", "p.store_id IS NULL", "p.rule_version DESC");
    }

    @Test
    void planInsertUsesNamedPlanParameterAndOriginalQueryUsesRawComparison() throws Exception {
        Method insert = CommissionMapper.class.getMethod("insertPlan", CommissionPlan.class, long.class);
        Method originals = CommissionMapper.class.getMethod("findOriginalBillLedgers", long.class);

        String insertSql = String.join(" ", insert.getAnnotation(Insert.class).value());
        String originalSql = String.join(" ", originals.getAnnotation(Select.class).value());

        assertThat(insertSql).contains("#{plan.code}", "#{plan.effectiveFrom}");
        assertThat(originalSql).contains("l.commission_amount >= 0").doesNotContain("&gt;");
    }
}
