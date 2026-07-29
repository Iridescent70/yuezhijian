package com.yuezhijian.server.notification;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper {
    String ANNOUNCEMENT_SELECT = """
            SELECT message.id, message.notification_no AS notificationNo, message.title, message.body,
                   message.scope_type AS scopeType, message.valid_from AS validFrom,
                   message.valid_to AS validTo, message.priority, message.pinned, message.status,
                   message.published_at AS publishedAt, message.created_at AS createdAt,
                   message.updated_at AS updatedAt, message.updated_by AS updatedBy,
                   COALESCE(operator.full_name, operator.username, N'系统任务') AS updatedByName,
                   CONVERT(varchar(18), message.row_version, 1) AS version
            FROM dbo.ntf_message message
            LEFT JOIN dbo.iam_user operator ON operator.id = message.updated_by
            """;

    String NOTIFICATION_SELECT = """
            SELECT message.id, message.notification_no AS notificationNo,
                   message.message_type AS messageType, message.event_code AS eventCode,
                   message.title, message.body, message.business_type AS businessType,
                   message.business_id AS businessId, message.route, message.priority, message.pinned,
                   message.published_at AS publishedAt, message.valid_to AS validTo,
                   CASE WHEN reading.message_id IS NULL THEN CAST(0 AS bit) ELSE CAST(1 AS bit) END AS [read],
                   reading.read_at AS readAt
            FROM dbo.ntf_message message
            LEFT JOIN dbo.ntf_message_read reading
              ON reading.message_id = message.id AND reading.user_id = #{userId}
            """;

    String VISIBLE_PREDICATE = """
            message.status = 'PUBLISHED'
            AND message.published_at IS NOT NULL AND message.published_at &lt;= #{now}
            AND (message.valid_from IS NULL OR message.valid_from &lt;= #{now})
            AND (message.valid_to IS NULL OR message.valid_to &gt;= #{now})
            AND (message.scope_type = 'ALL' OR EXISTS (
                SELECT 1 FROM dbo.ntf_message_store target
                WHERE target.message_id = message.id AND target.store_id = #{storeId}
            ))
            """;

    @Select("""
            <script>
            """ + ANNOUNCEMENT_SELECT + """
            WHERE message.message_type = 'ANNOUNCEMENT'
            <if test="storeId != null">
              AND (message.scope_type = 'ALL' OR EXISTS (
                SELECT 1 FROM dbo.ntf_message_store target
                WHERE target.message_id = message.id AND target.store_id = #{storeId}
              ))
            </if>
            <if test="keyword != null">
              AND (message.title LIKE CONCAT('%', #{keyword}, '%')
                   OR message.body LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND message.status = #{status}</if>
            ORDER BY message.updated_at DESC, message.id DESC
            OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY
            </script>
            """)
    List<AnnouncementRow> findAnnouncements(
            @Param("storeId") Long storeId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT_BIG(1)
            FROM dbo.ntf_message message
            WHERE message.message_type = 'ANNOUNCEMENT'
            <if test="storeId != null">
              AND (message.scope_type = 'ALL' OR EXISTS (
                SELECT 1 FROM dbo.ntf_message_store target
                WHERE target.message_id = message.id AND target.store_id = #{storeId}
              ))
            </if>
            <if test="keyword != null">
              AND (message.title LIKE CONCAT('%', #{keyword}, '%')
                   OR message.body LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND message.status = #{status}</if>
            </script>
            """)
    long countAnnouncements(
            @Param("storeId") Long storeId,
            @Param("keyword") String keyword,
            @Param("status") String status);

    @Select(ANNOUNCEMENT_SELECT + " WHERE message.id = #{id} AND message.message_type = 'ANNOUNCEMENT'")
    AnnouncementRow findAnnouncement(long id);

    @Select("""
            SELECT store_id
            FROM dbo.ntf_message_store
            WHERE message_id = #{messageId}
            ORDER BY store_id
            """)
    List<Long> findStoreIds(long messageId);

    @Select(value = """
            INSERT INTO dbo.ntf_message (
                notification_no, message_type, event_code, title, body, scope_type,
                valid_from, valid_to, priority, pinned, status, published_at, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{notificationNo}, 'ANNOUNCEMENT', 'ANNOUNCEMENT', #{title}, #{body}, #{scopeType},
                #{validFrom}, #{validTo}, #{priority}, #{pinned}, #{status},
                CASE WHEN #{status} = 'PUBLISHED' THEN sysdatetime() ELSE NULL END,
                #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insertAnnouncement(NewAnnouncement announcement);

    @Update("""
            UPDATE dbo.ntf_message
            SET title = #{title}, body = #{body}, scope_type = #{scopeType},
                valid_from = #{validFrom}, valid_to = #{validTo}, priority = #{priority},
                pinned = #{pinned}, status = #{status},
                published_at = CASE
                    WHEN #{status} = 'PUBLISHED' AND published_at IS NULL THEN sysdatetime()
                    ELSE published_at END,
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND message_type = 'ANNOUNCEMENT'
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updateAnnouncement(AnnouncementUpdate update);

    @Delete("DELETE FROM dbo.ntf_message_store WHERE message_id = #{messageId}")
    void deleteStores(long messageId);

    @Insert("""
            <script>
            INSERT INTO dbo.ntf_message_store (message_id, store_id, created_by)
            VALUES
            <foreach collection="storeIds" item="storeId" separator=",">
                (#{messageId}, #{storeId}, #{operatorId})
            </foreach>
            </script>
            """)
    void insertStores(
            @Param("messageId") long messageId,
            @Param("storeIds") List<Long> storeIds,
            @Param("operatorId") long operatorId);

    @Select("""
            <script>
            """ + NOTIFICATION_SELECT + """
            WHERE """ + VISIBLE_PREDICATE + """
            <if test="messageType != null">AND message.message_type = #{messageType}</if>
            <if test="readStatus == 'READ'">AND reading.message_id IS NOT NULL</if>
            <if test="readStatus == 'UNREAD'">AND reading.message_id IS NULL</if>
            <if test="publishedFrom != null">AND message.published_at &gt;= #{publishedFrom}</if>
            <if test="publishedTo != null">AND message.published_at &lt; #{publishedTo}</if>
            ORDER BY message.pinned DESC, message.priority DESC, message.published_at DESC, message.id DESC
            OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY
            </script>
            """)
    List<NotificationItem> findNotifications(NotificationQuery query);

    @Select("""
            <script>
            SELECT COUNT_BIG(1)
            FROM dbo.ntf_message message
            LEFT JOIN dbo.ntf_message_read reading
              ON reading.message_id = message.id AND reading.user_id = #{userId}
            WHERE """ + VISIBLE_PREDICATE + """
            <if test="messageType != null">AND message.message_type = #{messageType}</if>
            <if test="readStatus == 'READ'">AND reading.message_id IS NOT NULL</if>
            <if test="readStatus == 'UNREAD'">AND reading.message_id IS NULL</if>
            <if test="publishedFrom != null">AND message.published_at &gt;= #{publishedFrom}</if>
            <if test="publishedTo != null">AND message.published_at &lt; #{publishedTo}</if>
            </script>
            """)
    long countNotifications(NotificationQuery query);

    @Select("""
            <script>
            """ + NOTIFICATION_SELECT + """
            WHERE message.id = #{id} AND """ + VISIBLE_PREDICATE + """
            </script>
            """)
    NotificationItem findNotification(
            @Param("id") long id,
            @Param("userId") long userId,
            @Param("storeId") long storeId,
            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO dbo.ntf_message_read (message_id, user_id, read_at)
            SELECT #{messageId}, #{userId}, #{readAt}
            WHERE NOT EXISTS (
                SELECT 1 FROM dbo.ntf_message_read WITH (UPDLOCK, HOLDLOCK)
                WHERE message_id = #{messageId} AND user_id = #{userId}
            )
            """)
    int markRead(
            @Param("messageId") long messageId,
            @Param("userId") long userId,
            @Param("readAt") LocalDateTime readAt);

    @Insert("""
            <script>
            INSERT INTO dbo.ntf_message_read (message_id, user_id, read_at)
            SELECT message.id, #{userId}, #{now}
            FROM dbo.ntf_message message
            WHERE """ + VISIBLE_PREDICATE + """
              AND NOT EXISTS (
                SELECT 1 FROM dbo.ntf_message_read reading WITH (UPDLOCK, HOLDLOCK)
                WHERE reading.message_id = message.id AND reading.user_id = #{userId}
              )
            <if test="messageType != null">AND message.message_type = #{messageType}</if>
            </script>
            """)
    int markAllRead(NotificationQuery query);

    @Select("""
            <script>
            SELECT COUNT_BIG(1)
            FROM dbo.ntf_message message
            WHERE """ + VISIBLE_PREDICATE + """
              AND NOT EXISTS (
                SELECT 1 FROM dbo.ntf_message_read reading
                WHERE reading.message_id = message.id AND reading.user_id = #{userId}
              )
            </script>
            """)
    long unreadCount(
            @Param("userId") long userId,
            @Param("storeId") long storeId,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT id FROM dbo.ntf_message
            WHERE event_code = #{eventCode} AND business_type = #{businessType}
              AND business_id = #{businessId}
            """)
    Long findBusinessMessageId(
            @Param("eventCode") String eventCode,
            @Param("businessType") String businessType,
            @Param("businessId") long businessId);

    @Select(value = """
            INSERT INTO dbo.ntf_message (
                notification_no, message_type, event_code, title, body, scope_type,
                business_type, business_id, route, priority, status, published_at,
                created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{notificationNo}, #{messageType}, #{eventCode}, #{title}, #{body}, 'STORES',
                #{businessType}, #{businessId}, #{route}, #{priority}, 'PUBLISHED', sysdatetime(),
                #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insertBusinessNotification(BusinessNotificationDraft notification);

    @Select("""
            <script>
            SELECT template.id, template.event_code AS eventCode, template.event_name AS eventName,
                   template.channel, template.title_template AS titleTemplate,
                   template.body_template AS bodyTemplate, template.variables_csv AS variablesCsv,
                   template.status, template.updated_at AS updatedAt, template.updated_by AS updatedBy,
                   COALESCE(operator.full_name, operator.username, N'系统初始化') AS updatedByName,
                   CONVERT(varchar(18), template.row_version, 1) AS version
            FROM dbo.ntf_template template
            LEFT JOIN dbo.iam_user operator ON operator.id = template.updated_by
            WHERE 1 = 1
            <if test="keyword != null">
              AND (template.event_code LIKE CONCAT('%', #{keyword}, '%')
                   OR template.event_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND template.status = #{status}</if>
            ORDER BY template.event_code
            </script>
            """)
    List<NotificationTemplateRow> findTemplates(
            @Param("keyword") String keyword, @Param("status") String status);

    @Select("""
            SELECT template.id, template.event_code AS eventCode, template.event_name AS eventName,
                   template.channel, template.title_template AS titleTemplate,
                   template.body_template AS bodyTemplate, template.variables_csv AS variablesCsv,
                   template.status, template.updated_at AS updatedAt, template.updated_by AS updatedBy,
                   COALESCE(operator.full_name, operator.username, N'系统初始化') AS updatedByName,
                   CONVERT(varchar(18), template.row_version, 1) AS version
            FROM dbo.ntf_template template
            LEFT JOIN dbo.iam_user operator ON operator.id = template.updated_by
            WHERE template.id = #{id}
            """)
    NotificationTemplateRow findTemplate(long id);

    @Select("""
            SELECT template.id, template.event_code AS eventCode, template.event_name AS eventName,
                   template.channel, template.title_template AS titleTemplate,
                   template.body_template AS bodyTemplate, template.variables_csv AS variablesCsv,
                   template.status, template.updated_at AS updatedAt, template.updated_by AS updatedBy,
                   COALESCE(operator.full_name, operator.username, N'系统初始化') AS updatedByName,
                   CONVERT(varchar(18), template.row_version, 1) AS version
            FROM dbo.ntf_template template
            LEFT JOIN dbo.iam_user operator ON operator.id = template.updated_by
            WHERE template.event_code = #{eventCode} AND template.status = 'ACTIVE'
            """)
    NotificationTemplateRow findActiveTemplate(String eventCode);

    @Select(value = """
            INSERT INTO dbo.ntf_template (
                event_code, event_name, channel, title_template, body_template,
                variables_csv, status, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{eventCode}, #{eventName}, 'IN_APP', #{titleTemplate}, #{bodyTemplate},
                #{variablesCsv}, #{status}, #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insertTemplate(NewNotificationTemplate template);

    @Update("""
            UPDATE dbo.ntf_template
            SET event_name = #{eventName}, title_template = #{titleTemplate},
                body_template = #{bodyTemplate}, variables_csv = #{variablesCsv},
                status = #{status}, updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updateTemplate(NotificationTemplateUpdate update);
}
