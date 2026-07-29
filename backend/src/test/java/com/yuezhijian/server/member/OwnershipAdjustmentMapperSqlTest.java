package com.yuezhijian.server.member;

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

class OwnershipAdjustmentMapperSqlTest {
    @Test
    void searchAndCreateSqlKeepFiltersMemberVersionAndSingleActiveRequest() throws Exception {
        Method searchMethod = OwnershipAdjustmentMapper.class.getMethod("search", OwnershipAdjustmentQuery.class);
        SqlSource searchSource = new XMLLanguageDriver().createSqlSource(
                new Configuration(), String.join(" ", searchMethod.getAnnotation(Select.class).value()), Map.class);
        BoundSql searchSql = searchSource.getBoundSql(Map.of(
                "query", new OwnershipAdjustmentQuery(1L, "PENDING", "WAITING")));
        assertThat(searchSql.getSql()).contains(
                "adjustment.member_id = ?", "adjustment.approval_status = ?", "adjustment.execution_status = ?");

        Method insertMethod = OwnershipAdjustmentMapper.class.getMethod(
                "insert", OwnershipAdjustmentDraft.class, byte[].class);
        String insertSql = String.join(" ", insertMethod.getAnnotation(Select.class).value());
        assertThat(insertSql).contains(
                "member.row_version = #{memberRowVersion}",
                "active_adjustment.execution_status IN ('WAITING', 'PROCESSING')");
    }

    @Test
    void reviewClaimAndFinishAllUseExplicitStateAndRowVersion() throws Exception {
        Method reviewMethod = OwnershipAdjustmentMapper.class.getMethod(
                "review", long.class, boolean.class, String.class, String.class, long.class);
        assertThat(String.join(" ", reviewMethod.getAnnotation(Update.class).value()))
                .contains("approval_status = 'PENDING'", "execution_status = 'WAITING'",
                        "row_version = CONVERT(binary(8), #{version}, 1)");

        Method claimMethod = OwnershipAdjustmentMapper.class.getMethod(
                "claim", long.class, String.class, LocalDate.class);
        assertThat(String.join(" ", claimMethod.getAnnotation(Update.class).value()))
                .contains("execution_status = 'PROCESSING'", "effective_date <= #{businessDate}");

        Method finishMethod = OwnershipAdjustmentMapper.class.getMethod(
                "finish", long.class, boolean.class, String.class, String.class);
        assertThat(String.join(" ", finishMethod.getAnnotation(Update.class).value()))
                .contains("execution_status = 'PROCESSING'", "'APPLIED'", "'FAILED'",
                        "row_version = CONVERT(binary(8), #{version}, 1)");
    }
}
