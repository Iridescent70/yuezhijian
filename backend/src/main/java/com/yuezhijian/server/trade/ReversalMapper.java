package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReversalMapper {
    String SUMMARY_SELECT = """
            SELECT reversal.id, reversal.reversal_no AS reversalNo, reversal.bill_id AS billId,
                   bill.bill_no AS billNo, COALESCE(member.full_name, bill.guest_name) AS customerName,
                   store.store_name AS storeName, reversal.refund_amount AS refundAmount,
                   reversal.status, reversal.reason, reversal.requested_at AS requestedAt,
                   reversal.requested_by AS requestedBy, reversal.reviewed_at AS reviewedAt,
                   reversal.reviewed_by AS reviewedBy, reversal.review_comment AS reviewComment,
                   reversal.executed_at AS executedAt,
                   CONVERT(varchar(18), reversal.row_version, 1) AS version
            FROM dbo.trd_reversal reversal
            JOIN dbo.trd_bill bill ON bill.id = reversal.bill_id
            LEFT JOIN dbo.mem_member member ON member.id = bill.member_id
            JOIN dbo.org_store store ON store.id = bill.store_id
            """;

    @Select("""
            <script>
            """ + SUMMARY_SELECT + """
            WHERE 1 = 1
            <if test="status != null">AND reversal.status = #{status}</if>
            ORDER BY reversal.requested_at DESC, reversal.id DESC
            </script>
            """)
    List<ReversalSummary> search(@Param("status") String status);

    @Select(SUMMARY_SELECT + " WHERE reversal.id = #{id}")
    ReversalSummary findById(long id);

    @Select(SUMMARY_SELECT + " WHERE reversal.request_idempotency_key = #{key}")
    ReversalSummary findByRequestKey(String key);

    @Select(SUMMARY_SELECT + " WHERE reversal.execution_idempotency_key = #{key}")
    ReversalSummary findByExecutionKey(String key);

    @Select(SUMMARY_SELECT + " WHERE reversal.bill_id = #{billId} AND reversal.status IN ('SUBMITTED','APPROVED','EXECUTED')")
    ReversalSummary findActiveByBill(long billId);

    @Select(value = """
            INSERT INTO dbo.trd_reversal (
                reversal_no, bill_id, refund_amount, reason, request_idempotency_key, requested_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{reversalNo}, #{bill.bill.id}, #{bill.bill.receivableAmount},
                #{reason}, #{idempotencyKey}, #{operatorId}
            )
            """, affectData = true)
    long insert(ReversalDraft draft);

    @Update("""
            UPDATE dbo.trd_reversal
            SET status = #{status}, reviewed_at = sysdatetime(), reviewed_by = #{operatorId},
                review_comment = #{comment}
            WHERE id = #{id} AND status = 'SUBMITTED'
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int review(
            @Param("id") long id,
            @Param("status") String status,
            @Param("comment") String comment,
            @Param("version") String version,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.trd_reversal
            SET status = 'EXECUTED', execution_idempotency_key = #{key},
                executed_at = sysdatetime(), executed_by = #{operatorId}
            WHERE id = #{id} AND status = 'APPROVED'
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int markExecuted(
            @Param("id") long id,
            @Param("version") String version,
            @Param("key") String key,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.trd_bill
            SET status = 'REVERSED', updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{billId} AND status = 'SETTLED'
            """)
    int reverseBill(@Param("billId") long billId, @Param("operatorId") long operatorId);

    @Insert("""
            INSERT INTO dbo.trd_payment_refund (
                refund_no, reversal_id, payment_id, refund_amount, refund_status,
                completed_at, idempotency_key, created_by
            ) VALUES (
                #{refundNo}, #{reversalId}, #{paymentId}, #{amount}, 'SUCCESS',
                sysdatetime(), #{idempotencyKey}, #{operatorId}
            )
            """)
    void insertPaymentRefund(
            @Param("refundNo") String refundNo,
            @Param("reversalId") long reversalId,
            @Param("paymentId") long paymentId,
            @Param("amount") BigDecimal amount,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.trd_payment SET payment_status = 'REFUNDED'
            WHERE id = #{paymentId} AND payment_status = 'SUCCESS'
            """)
    int markPaymentRefunded(long paymentId);
}
