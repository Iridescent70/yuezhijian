package com.yuezhijian.server.member;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OwnershipAdjustmentMapper {
    String ADJUSTMENT_SELECT = """
            SELECT adjustment.id, adjustment.adjustment_no AS adjustmentNo,
                   adjustment.member_id AS memberId, member.member_no AS memberNo,
                   member.full_name AS memberName,
                   adjustment.old_store_id AS oldStoreId, old_store.store_name AS oldStoreName,
                   adjustment.new_store_id AS newStoreId, new_store.store_name AS newStoreName,
                   adjustment.effective_date AS effectiveDate,
                   adjustment.share_rule_json AS shareRuleJson, adjustment.reason,
                   adjustment.approval_status AS approvalStatus,
                   adjustment.execution_status AS executionStatus,
                   adjustment.requested_by AS requestedBy, adjustment.requested_at AS requestedAt,
                   adjustment.reviewed_by AS reviewedBy, adjustment.reviewed_at AS reviewedAt,
                   adjustment.review_comment AS reviewComment,
                   adjustment.applied_at AS appliedAt, adjustment.execution_message AS executionMessage,
                   CONVERT(varchar(18), adjustment.row_version, 1) AS version
            FROM dbo.mem_ownership_adjustment adjustment
            JOIN dbo.mem_member member ON member.id = adjustment.member_id
            JOIN dbo.org_store old_store ON old_store.id = adjustment.old_store_id
            JOIN dbo.org_store new_store ON new_store.id = adjustment.new_store_id
            """;

    @Select("""
            <script>
            """ + ADJUSTMENT_SELECT + """
            WHERE 1 = 1
            <if test="query.memberId != null">AND adjustment.member_id = #{query.memberId}</if>
            <if test="query.approvalStatus != null">AND adjustment.approval_status = #{query.approvalStatus}</if>
            <if test="query.executionStatus != null">AND adjustment.execution_status = #{query.executionStatus}</if>
            ORDER BY adjustment.requested_at DESC, adjustment.id DESC
            </script>
            """)
    List<OwnershipAdjustmentRow> search(@Param("query") OwnershipAdjustmentQuery query);

    @Select(ADJUSTMENT_SELECT + " WHERE adjustment.id = #{id}")
    OwnershipAdjustmentRow findById(long id);

    @Select("""
            SELECT COUNT(1) FROM dbo.mem_ownership_adjustment
            WHERE member_id = #{memberId} AND execution_status IN ('WAITING', 'PROCESSING')
            """)
    int countActive(long memberId);

    @Select(value = """
            INSERT INTO dbo.mem_ownership_adjustment (
                adjustment_no, member_id, old_store_id, new_store_id, effective_date,
                share_rule_json, reason, requested_by
            )
            OUTPUT INSERTED.id
            SELECT #{draft.adjustmentNo}, member.id, #{draft.oldStoreId}, #{draft.newStoreId},
                   #{draft.effectiveDate}, #{draft.shareRuleJson}, #{draft.reason}, #{draft.requestedBy}
            FROM dbo.mem_member member
            WHERE member.id = #{draft.memberId}
              AND member.owner_store_id = #{draft.oldStoreId}
              AND member.row_version = #{memberRowVersion}
              AND NOT EXISTS (
                  SELECT 1 FROM dbo.mem_ownership_adjustment active_adjustment
                  WHERE active_adjustment.member_id = member.id
                    AND active_adjustment.execution_status IN ('WAITING', 'PROCESSING')
              )
            """, affectData = true)
    Long insert(
            @Param("draft") OwnershipAdjustmentDraft draft,
            @Param("memberRowVersion") byte[] memberRowVersion);

    @Update("""
            UPDATE dbo.mem_ownership_adjustment
            SET approval_status = CASE WHEN #{approved} = 1 THEN 'APPROVED' ELSE 'REJECTED' END,
                execution_status = CASE WHEN #{approved} = 1 THEN 'WAITING' ELSE 'CANCELLED' END,
                reviewed_by = #{operatorId}, reviewed_at = sysdatetime(), review_comment = #{comment}
            WHERE id = #{id} AND approval_status = 'PENDING' AND execution_status = 'WAITING'
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int review(
            @Param("id") long id,
            @Param("approved") boolean approved,
            @Param("comment") String comment,
            @Param("version") String version,
            @Param("operatorId") long operatorId);

    @Select(ADJUSTMENT_SELECT + """
             WHERE adjustment.approval_status = 'APPROVED'
               AND adjustment.execution_status = 'WAITING'
               AND adjustment.effective_date <= #{businessDate}
             ORDER BY adjustment.effective_date, adjustment.id
            """)
    List<OwnershipAdjustmentRow> findDue(LocalDate businessDate);

    @Update("""
            UPDATE dbo.mem_ownership_adjustment
            SET execution_status = 'PROCESSING'
            WHERE id = #{id} AND approval_status = 'APPROVED' AND execution_status = 'WAITING'
              AND effective_date <= #{businessDate}
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int claim(
            @Param("id") long id,
            @Param("version") String version,
            @Param("businessDate") LocalDate businessDate);

    @Update("""
            UPDATE dbo.mem_ownership_adjustment
            SET execution_status = CASE WHEN #{applied} = 1 THEN 'APPLIED' ELSE 'FAILED' END,
                applied_at = CASE WHEN #{applied} = 1 THEN sysdatetime() ELSE NULL END,
                execution_message = #{message}
            WHERE id = #{id} AND approval_status = 'APPROVED' AND execution_status = 'PROCESSING'
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int finish(
            @Param("id") long id,
            @Param("applied") boolean applied,
            @Param("message") String message,
            @Param("version") String version);
}
