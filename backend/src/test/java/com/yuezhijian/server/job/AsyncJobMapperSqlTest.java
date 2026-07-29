package com.yuezhijian.server.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class AsyncJobMapperSqlTest {
    @Test
    void claimUsesLocksAndCanSafelyReclaimExpiredJobs() throws Exception {
        Method method = AsyncJobMapper.class.getMethod(
                "claimNext", String.class, java.time.LocalDateTime.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains(
                "UPDLOCK", "READPAST", "ROWLOCK", "status = 'PENDING'",
                "status = 'RUNNING'", "lease_expires_at", "lease_token = #{leaseToken}",
                "attempt_count = attempt_count + 1", "OUTPUT INSERTED.id");
        Method complete = AsyncJobMapper.class.getMethod(
                "complete", long.class, String.class, String.class, int.class, int.class, long.class);
        String completeSql = String.join(" ", complete.getAnnotation(Update.class).value());
        assertThat(completeSql).contains("lease_token = #{leaseToken}");
    }

    @Test
    void listAndCancelAreAlwaysScopedToTheCreator() throws Exception {
        Method cancel = AsyncJobMapper.class.getMethod("cancel", long.class, long.class);
        String cancelSql = String.join(" ", cancel.getAnnotation(Update.class).value());

        assertThat(AsyncJobMapper.ITEM_SELECT).contains("job.created_by AS createdBy");
        assertThat(cancelSql).contains(
                "created_by = #{createdBy}", "status = 'PENDING'", "status = 'CANCELLED'");
        Method cleanup = AsyncJobMapper.class.getMethod("findExpiredResults", int.class);
        String cleanupSql = String.join(" ", cleanup.getAnnotation(Select.class).value());
        assertThat(cleanupSql).contains(
                "READPAST", "expires_at <= sysdatetime()", "result_purged_at IS NULL", "TOP (#{limit})");
    }
}
