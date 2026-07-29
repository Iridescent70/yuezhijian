package com.yuezhijian.server.feedback;

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

class FeedbackMapperSqlTest {
    @Test
    void feedbackQueryCanParseAllOptionalFilters() throws Exception {
        Method method = FeedbackMapper.class.getMethod("findFeedback", FeedbackQuery.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        FeedbackQuery query = new FeedbackQuery(2L, 101L, 2, "PROCESSING", true, "账单");

        BoundSql sql = source.getBoundSql(Map.of("query", query));

        assertThat(sql.getSql()).contains(
                "feedback.store_id = ?", "feedback.handler_id = ?", "feedback.score = ?",
                "feedback.status = ?", "sysdatetime() > feedback.due_at",
                "feedback.feedback_no LIKE ?");
        assertThat(sql.getParameterMappings()).isNotEmpty();
    }

    @Test
    void feedbackUpdateUsesExpectedStatusAndPreservesHistoryFields() throws Exception {
        Method method = FeedbackMapper.class.getMethod("updateFeedback", FeedbackUpdate.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertThat(sql).contains(
                "status = #{update.status}", "id = #{update.id} AND status = #{update.expectedStatus}",
                "WHEN #{update.actionType} = 'REOPENED' THEN NULL",
                "WHEN #{update.actionType} = 'RESOLVED' THEN sysdatetime()",
                "THEN #{update.dueHours}", "THEN #{update.dueAt}");
    }
}
