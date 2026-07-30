package com.yuezhijian.server.benefit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BenefitMapper {
    String DEFINITION_COLUMNS = """
            id, voucher_code AS code, voucher_name AS name, benefit_type AS benefitType,
            face_amount AS faceAmount, discount_rate AS discountRate, min_spend AS minSpend,
            valid_days AS validDays, commission_rule AS commissionRule, status,
            CONVERT(varchar(18), CAST(row_version AS varbinary(8)), 1) AS version
            """;

    String CODE_SELECT = """
            SELECT code.id, code.code, code.voucher_id AS voucherId,
                   code.voucher_code AS voucherCode, code.voucher_name AS voucherName,
                   code.benefit_type AS benefitType, code.face_amount AS faceAmount,
                   code.discount_rate AS discountRate, code.min_spend AS minSpend,
                   code.member_id AS memberId, member.full_name AS memberName,
                   code.valid_from AS validFrom, code.valid_until AS validUntil, code.status,
                   code.bound_at AS boundAt, code.redeemed_at AS redeemedAt,
                   code.redeemed_bill_id AS redeemedBillId,
                   CONVERT(varchar(18), CAST(code.row_version AS varbinary(8)), 1) AS version
            FROM dbo.ben_voucher_code code
            LEFT JOIN dbo.mem_member member ON member.id = code.member_id
            """;

    @Select("""
            <script>
            SELECT """ + DEFINITION_COLUMNS + """
            FROM dbo.cat_voucher
            WHERE 1 = 1
            <if test="keyword != null">
              AND (voucher_code LIKE CONCAT('%', #{keyword}, '%') OR voucher_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND status = #{status}</if>
            ORDER BY id DESC
            </script>
            """)
    List<VoucherDefinition> findDefinitions(@Param("keyword") String keyword, @Param("status") String status);

    @Select("SELECT " + DEFINITION_COLUMNS + " FROM dbo.cat_voucher WHERE id = #{id}")
    VoucherDefinition findDefinition(long id);

    @Select("SELECT COUNT(1) FROM dbo.cat_voucher WHERE UPPER(voucher_code) = UPPER(#{code})")
    int countDefinitionCode(String code);

    @Insert(value = """
            INSERT INTO dbo.cat_voucher (
                voucher_code, voucher_name, benefit_type, face_amount, discount_rate,
                min_spend, valid_days, commission_rule, status, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{draft.code}, #{draft.name}, #{draft.benefitType}, #{draft.faceAmount}, #{draft.discountRate},
                #{draft.minSpend}, #{draft.validDays}, #{draft.commissionRule}, #{draft.status},
                #{operatorId}, #{operatorId}
            )
            """)
    long insertDefinition(@Param("draft") VoucherDefinition draft, @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.cat_voucher
            SET voucher_name = #{draft.name}, benefit_type = #{draft.benefitType},
                face_amount = #{draft.faceAmount}, discount_rate = #{draft.discountRate},
                min_spend = #{draft.minSpend}, valid_days = #{draft.validDays},
                commission_rule = #{draft.commissionRule}, status = #{draft.status},
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{draft.id} AND row_version = CONVERT(varbinary(8), #{draft.version}, 1)
            """)
    int updateDefinition(@Param("draft") VoucherDefinition draft, @Param("operatorId") long operatorId);

    @Select("""
            <script>
            """ + CODE_SELECT + """
            WHERE 1 = 1
            <if test="memberId != null">AND code.member_id = #{memberId}</if>
            <if test="status != null">AND code.status = #{status}</if>
            <if test="keyword != null">
              AND (code.code LIKE CONCAT('%', #{keyword}, '%') OR code.voucher_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY code.id DESC
            </script>
            """)
    List<VoucherCodeSummary> findVoucherCodes(
            @Param("memberId") Long memberId, @Param("status") String status, @Param("keyword") String keyword);

    @Select(CODE_SELECT + " WHERE UPPER(code.code) = UPPER(#{codeValue})")
    VoucherCodeSummary findVoucherCodeByCode(@Param("codeValue") String code);

    @Select(CODE_SELECT + " WHERE code.id = #{id}")
    VoucherCodeSummary findVoucherCodeById(long id);

    @Select(CODE_SELECT + " JOIN dbo.ben_voucher_issue_batch batch ON batch.id = code.issue_batch_id"
            + " WHERE batch.idempotency_key = #{key} ORDER BY code.id")
    List<VoucherCodeSummary> findIssueByKey(String key);

    @Insert(value = """
            INSERT INTO dbo.ben_voucher_issue_batch (
                batch_no, voucher_id, member_id, issue_count, idempotency_key, created_by
            )
            OUTPUT INSERTED.id
            VALUES (#{draft.batchNo}, #{draft.definition.id}, #{draft.memberId}, #{draft.count},
                    #{draft.idempotencyKey}, #{draft.operatorId})
            """)
    long insertIssueBatch(@Param("draft") VoucherIssueDraft draft);

    @Insert("""
            INSERT INTO dbo.ben_voucher_code (
                code, issue_batch_id, voucher_id, voucher_code, voucher_name, benefit_type,
                face_amount, discount_rate, min_spend, member_id, valid_from, valid_until,
                status, bound_at, created_by, updated_by
            ) VALUES (
                #{code}, #{batchId}, #{draft.definition.id}, #{draft.definition.code},
                #{draft.definition.name}, #{draft.definition.benefitType}, #{draft.definition.faceAmount},
                #{draft.definition.discountRate}, #{draft.definition.minSpend}, #{draft.memberId},
                #{draft.validFrom}, #{draft.validUntil},
                CASE WHEN #{draft.memberId} IS NULL THEN 'UNBOUND' ELSE 'BOUND' END,
                CASE WHEN #{draft.memberId} IS NULL THEN NULL ELSE sysdatetime() END,
                #{draft.operatorId}, #{draft.operatorId}
            )
            """)
    void insertVoucherCode(
            @Param("batchId") long batchId,
            @Param("code") String code,
            @Param("draft") VoucherIssueDraft draft);

    @Select("""
            SELECT code_id
            FROM dbo.ben_voucher_ledger
            WHERE idempotency_key = #{key} AND ledger_type = 'BIND'
            """)
    Long findBoundCodeIdByKey(String key);

    @Update("""
            UPDATE dbo.ben_voucher_code
            SET member_id = #{command.memberId}, status = 'BOUND', bound_at = sysdatetime(),
                updated_at = sysdatetime(), updated_by = #{command.operatorId}
            WHERE id = #{command.voucher.id} AND status = 'UNBOUND'
              AND valid_from <= sysdatetime() AND valid_until >= sysdatetime()
              AND row_version = CONVERT(varbinary(8), #{command.voucher.version}, 1)
            """)
    int bindVoucher(@Param("command") VoucherBindCommand command);

    @Insert("""
            INSERT INTO dbo.ben_voucher_ledger (
                ledger_no, code_id, ledger_type, member_id, amount, idempotency_key, note, created_by
            ) VALUES (
                #{ledgerNo}, #{command.voucher.id}, 'BIND', #{command.memberId}, 0,
                #{command.idempotencyKey}, N'绑定会员', #{command.operatorId}
            )
            """)
    void insertBindLedger(@Param("ledgerNo") String ledgerNo, @Param("command") VoucherBindCommand command);

    @Update("""
            UPDATE dbo.ben_voucher_code
            SET status = 'REDEEMED', redeemed_at = sysdatetime(), redeemed_bill_id = #{command.billId},
                updated_at = sysdatetime(), updated_by = #{command.operatorId}
            WHERE id = #{command.voucherCodeId} AND member_id = #{command.memberId} AND status = 'BOUND'
              AND valid_from <= sysdatetime() AND valid_until >= sysdatetime()
              AND row_version = CONVERT(varbinary(8), #{command.voucherVersion}, 1)
            """)
    int redeemVoucher(@Param("command") VoucherSettlementConsumption command);

    @Insert(value = """
            INSERT INTO dbo.ben_voucher_ledger (
                ledger_no, code_id, ledger_type, member_id, amount, source_bill_id, note, created_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{ledgerNo}, #{command.voucherCodeId}, 'REDEEM', #{command.memberId},
                #{command.amount}, #{command.billId}, #{command.displayName}, #{command.operatorId}
            )
            """)
    long insertRedeemLedger(
            @Param("ledgerNo") String ledgerNo, @Param("command") VoucherSettlementConsumption command);

    @Insert("""
            INSERT INTO dbo.trd_bill_asset_usage (
                bill_id, asset_type, member_id, voucher_code_id, quantity, amount,
                asset_ledger_id, display_name, created_by
            ) VALUES (
                #{command.billId}, 'VOUCHER', #{command.memberId}, #{command.voucherCodeId}, 1,
                #{command.amount}, #{ledgerId}, #{command.displayName}, #{command.operatorId}
            )
            """)
    void insertVoucherAssetUsage(
            @Param("command") VoucherSettlementConsumption command, @Param("ledgerId") long ledgerId);

    @Update("""
            UPDATE dbo.ben_voucher_code
            SET status = 'BOUND', redeemed_at = NULL, redeemed_bill_id = NULL,
                updated_at = sysdatetime(), updated_by = #{command.operatorId}
            WHERE id = #{command.voucherCodeId} AND status = 'REDEEMED'
              AND redeemed_bill_id = #{command.billId}
            """)
    int returnVoucher(@Param("command") VoucherRefundCommand command);

    @Insert("""
            INSERT INTO dbo.ben_voucher_ledger (
                ledger_no, code_id, ledger_type, member_id, amount, source_reversal_id,
                reversed_ledger_id, note, created_by
            )
            SELECT #{ledgerNo}, code.id, 'RETURN', code.member_id, ledger.amount, #{command.reversalId},
                   ledger.id, #{command.note}, #{command.operatorId}
            FROM dbo.ben_voucher_code code
            JOIN dbo.ben_voucher_ledger ledger ON ledger.id = #{command.redeemLedgerId}
            WHERE code.id = #{command.voucherCodeId} AND ledger.code_id = code.id
            """)
    int insertReturnLedger(@Param("ledgerNo") String ledgerNo, @Param("command") VoucherRefundCommand command);
}
