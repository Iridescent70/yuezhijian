package com.yuezhijian.server.cancelreason;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CancelReasonMapper {
    String REASON_SELECT = """
            SELECT reason.id, reason.business_type AS businessType, reason.reason_code AS code,
                   reason.reason_name AS name, reason.requires_note AS requiresNote,
                   reason.sort_no AS sortNo, reason.status, reason.updated_at AS updatedAt,
                   reason.updated_by AS updatedBy,
                   COALESCE(account.full_name, account.username, N'系统初始化') AS updatedByName,
                   CONVERT(varchar(18), reason.row_version, 1) AS version
            FROM dbo.sys_cancel_reason reason
            LEFT JOIN dbo.iam_user account ON account.id = reason.updated_by
            """;

    @Select("""
            <script>
            """ + REASON_SELECT + """
            WHERE 1 = 1
            <if test="businessType != null">AND reason.business_type = #{businessType}</if>
            <if test="keyword != null">
              AND (reason.reason_code LIKE CONCAT('%', #{keyword}, '%')
                   OR reason.reason_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND reason.status = #{status}</if>
            ORDER BY reason.business_type, reason.sort_no, reason.id
            </script>
            """)
    List<CancelReason> findAll(
            @Param("businessType") String businessType,
            @Param("keyword") String keyword,
            @Param("status") String status);

    @Select(REASON_SELECT + " WHERE reason.id = #{id}")
    CancelReason find(long id);

    @Select(REASON_SELECT + """
             WHERE reason.business_type = #{businessType}
               AND reason.reason_code = #{code} AND reason.status = 'ACTIVE'
            """)
    CancelReason findActive(@Param("businessType") String businessType, @Param("code") String code);

    @Select(value = """
            INSERT INTO dbo.sys_cancel_reason (
                business_type, reason_code, reason_name, requires_note, sort_no, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{businessType}, #{code}, #{name}, #{requiresNote}, #{sortNo}, #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insert(NewCancelReason reason);

    @Update("""
            UPDATE dbo.sys_cancel_reason
            SET reason_name = #{name}, requires_note = #{requiresNote}, sort_no = #{sortNo},
                status = #{status}, updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int update(CancelReasonUpdate update);
}
