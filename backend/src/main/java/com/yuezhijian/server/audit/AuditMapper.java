package com.yuezhijian.server.audit;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditMapper {
    String AUDIT_SELECT = """
            SELECT audit.id, audit.trace_id AS traceId, audit.user_id AS userId,
                   COALESCE(account.full_name, account.username,
                            CASE WHEN audit.user_id IS NULL THEN N'系统任务'
                                 ELSE CONCAT(N'用户#', audit.user_id) END) AS operatorName,
                   audit.store_id AS storeId, audit.module, audit.action,
                   audit.object_type AS objectType, audit.object_id AS objectId,
                   audit.before_json AS beforeJson, audit.after_json AS afterJson,
                   audit.result, audit.error_code AS errorCode, audit.ip,
                   audit.occurred_at AS occurredAt
            FROM dbo.sys_audit_log audit
            LEFT JOIN dbo.iam_user account ON account.id = audit.user_id
            """;

    @Insert("""
            INSERT INTO dbo.sys_audit_log (
                trace_id, user_id, store_id, module, action, object_type, object_id,
                before_json, after_json, result
            ) VALUES (
                #{traceId}, #{userId}, #{storeId}, #{module}, #{action}, #{objectType}, #{objectId},
                #{beforeJson}, #{afterJson}, 'SUCCESS'
            )
            """)
    void insert(NewAuditEvent event);

    @Select("""
            <script>
            """ + AUDIT_SELECT + """
            WHERE audit.object_type = #{objectType}
              AND audit.object_id = #{objectId}
              AND audit.result = 'SUCCESS'
              AND (
                audit.store_id IS NULL
                <if test="accessibleStoreIds != null and !accessibleStoreIds.isEmpty()">
                  OR audit.store_id IN
                  <foreach collection="accessibleStoreIds" item="storeId" open="(" separator="," close=")">
                    #{storeId}
                  </foreach>
                </if>
              )
            ORDER BY audit.occurred_at DESC, audit.id DESC
            </script>
            """)
    List<AuditLogRow> findHistory(
            @Param("objectType") String objectType,
            @Param("objectId") String objectId,
            @Param("accessibleStoreIds") List<Long> accessibleStoreIds);

    @Select("""
            <script>
            """ + AUDIT_SELECT + """
            WHERE 1 = 1
            <if test="query.userId != null">AND audit.user_id = #{query.userId}</if>
            <if test="query.operator != null">
              AND COALESCE(account.full_name, account.username,
                           CASE WHEN audit.user_id IS NULL THEN N'系统任务'
                                ELSE CONCAT(N'用户#', audit.user_id) END)
                  LIKE CONCAT('%', #{query.operator}, '%')
            </if>
            <if test="query.module != null">AND audit.module LIKE CONCAT('%', #{query.module}, '%')</if>
            <if test="query.action != null">AND audit.action LIKE CONCAT('%', #{query.action}, '%')</if>
            <if test="query.objectType != null">AND audit.object_type LIKE CONCAT('%', #{query.objectType}, '%')</if>
            <if test="query.objectId != null">AND audit.object_id LIKE CONCAT('%', #{query.objectId}, '%')</if>
            <if test="query.result != null">AND audit.result = #{query.result}</if>
            <if test="query.occurredFrom != null">AND audit.occurred_at &gt;= #{query.occurredFrom}</if>
            <if test="query.occurredTo != null">AND audit.occurred_at &lt; #{query.occurredTo}</if>
            ORDER BY audit.occurred_at DESC, audit.id DESC
            OFFSET #{query.offset} ROWS FETCH NEXT #{query.size} ROWS ONLY
            </script>
            """)
    List<AuditLogRow> findPage(@Param("query") AuditLogQuery query);

    @Select("""
            <script>
            SELECT COUNT(1) FROM dbo.sys_audit_log audit
            LEFT JOIN dbo.iam_user account ON account.id = audit.user_id
            WHERE 1 = 1
            <if test="query.userId != null">AND audit.user_id = #{query.userId}</if>
            <if test="query.operator != null">
              AND COALESCE(account.full_name, account.username,
                           CASE WHEN audit.user_id IS NULL THEN N'系统任务'
                                ELSE CONCAT(N'用户#', audit.user_id) END)
                  LIKE CONCAT('%', #{query.operator}, '%')
            </if>
            <if test="query.module != null">AND audit.module LIKE CONCAT('%', #{query.module}, '%')</if>
            <if test="query.action != null">AND audit.action LIKE CONCAT('%', #{query.action}, '%')</if>
            <if test="query.objectType != null">AND audit.object_type LIKE CONCAT('%', #{query.objectType}, '%')</if>
            <if test="query.objectId != null">AND audit.object_id LIKE CONCAT('%', #{query.objectId}, '%')</if>
            <if test="query.result != null">AND audit.result = #{query.result}</if>
            <if test="query.occurredFrom != null">AND audit.occurred_at &gt;= #{query.occurredFrom}</if>
            <if test="query.occurredTo != null">AND audit.occurred_at &lt; #{query.occurredTo}</if>
            </script>
            """)
    long count(@Param("query") AuditLogQuery query);

    @Select(AUDIT_SELECT + " WHERE audit.id = #{id}")
    AuditLogRow find(long id);
}
