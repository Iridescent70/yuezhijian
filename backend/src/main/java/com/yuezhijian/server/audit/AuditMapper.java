package com.yuezhijian.server.audit;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditMapper {
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
            SELECT audit.id, audit.trace_id AS traceId, audit.user_id AS userId,
                   COALESCE(account.full_name, CONCAT(N'用户#', audit.user_id)) AS operatorName,
                   audit.store_id AS storeId, audit.module, audit.action,
                   audit.object_type AS objectType, audit.object_id AS objectId,
                   audit.before_json AS beforeJson, audit.after_json AS afterJson,
                   audit.occurred_at AS occurredAt
            FROM dbo.sys_audit_log audit
            LEFT JOIN dbo.iam_user account ON account.id = audit.user_id
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
}
