package com.yuezhijian.server.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class AsyncJobMapperSqlTest {
    @Test
    void claimUsesLocksAndTransitionsOnlyPendingJobs() throws Exception {
        Method method = AsyncJobMapper.class.getMethod("claimNext");
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains(
                "UPDLOCK", "READPAST", "ROWLOCK", "status = 'PENDING'",
                "status = 'RUNNING'", "OUTPUT INSERTED.id");
    }

    @Test
    void listAndCancelAreAlwaysScopedToTheCreator() throws Exception {
        Method cancel = AsyncJobMapper.class.getMethod("cancel", long.class, long.class);
        String cancelSql = String.join(" ", cancel.getAnnotation(Update.class).value());

        assertThat(AsyncJobMapper.ITEM_SELECT).contains("job.created_by AS createdBy");
        assertThat(cancelSql).contains(
                "created_by = #{createdBy}", "status = 'PENDING'", "status = 'CANCELLED'");
    }
}
