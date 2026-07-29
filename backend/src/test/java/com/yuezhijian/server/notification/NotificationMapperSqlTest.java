package com.yuezhijian.server.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class NotificationMapperSqlTest {
    @Test
    void notificationFeedParsesScopeReadDateAndPaginationFilters() throws Exception {
        NotificationQuery query = new NotificationQuery(
                7L, 2L, "BILL_REVERSAL", "UNREAD",
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 7, 30, 12, 0), 2, 20);
        BoundSql sql = parse(NotificationMapper.class.getMethod("findNotifications", NotificationQuery.class), query);

        assertThat(sql.getSql()).contains(
                "reading.user_id = ?", "message.status = 'PUBLISHED'",
                "target.store_id = ?", "message.message_type = ?", "reading.message_id IS NULL",
                "message.published_at >= ?", "message.published_at < ?",
                "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY").doesNotContain("&lt;");
        assertThat(sql.getParameterMappings()).hasSize(10);
    }

    @Test
    void readsAreIdempotentAndManagementUpdatesUseRowVersion() throws Exception {
        Method markRead = NotificationMapper.class.getMethod(
                "markRead", long.class, long.class, LocalDateTime.class);
        Method updateAnnouncement = NotificationMapper.class.getMethod(
                "updateAnnouncement", AnnouncementUpdate.class);
        Method updateTemplate = NotificationMapper.class.getMethod(
                "updateTemplate", NotificationTemplateUpdate.class);

        assertThat(String.join(" ", markRead.getAnnotation(Insert.class).value()))
                .contains("WITH (UPDLOCK, HOLDLOCK)", "WHERE NOT EXISTS");
        assertThat(String.join(" ", updateAnnouncement.getAnnotation(Update.class).value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)",
                        "published_at = CASE");
        assertThat(String.join(" ", updateTemplate.getAnnotation(Update.class).value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)");
    }

    private BoundSql parse(Method method, Object parameters) {
        Select select = method.getAnnotation(Select.class);
        SqlSource source = new XMLLanguageDriver().createSqlSource(
                new Configuration(), String.join(" ", select.value()), parameters.getClass());
        return source.getBoundSql(parameters);
    }
}
