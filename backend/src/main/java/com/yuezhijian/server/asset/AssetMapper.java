package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AssetMapper {
    String BALANCE_ACCOUNT_SELECT = """
            SELECT account.id AS accountId, account.member_id AS memberId,
                   account.available_balance AS availableBalance,
                   account.frozen_balance AS frozenBalance,
                   account.total_recharged AS totalRecharged,
                   account.last_transaction_at AS lastTransactionAt,
                   account.row_version AS rowVersion
            FROM dbo.ast_balance_account account
            """;

    String POINT_ACCOUNT_SELECT = """
            SELECT account.id AS accountId, account.member_id AS memberId,
                   account.available_points AS availablePoints,
                   account.lifetime_points AS lifetimePoints,
                   account.last_transaction_at AS lastTransactionAt,
                   account.row_version AS rowVersion
            FROM dbo.ast_point_account account
            """;

    String RECHARGE_SELECT = """
            SELECT recharge.id, recharge.recharge_no AS rechargeNo, quote.quote_no AS quoteNo,
                   recharge.member_id AS memberId, recharge.store_id AS storeId, store.store_name AS storeName,
                   recharge.recharge_amount AS rechargeAmount, recharge.gift_amount AS giftAmount,
                   recharge.credit_amount AS creditAmount,
                   recharge.payment_method_id AS paymentMethodId, method.method_name AS paymentMethodName,
                   recharge.external_reference AS externalReference,
                   recharge.sales_employee_id AS salesEmployeeId, recharge.status,
                   recharge.confirmed_at AS confirmedAt, recharge.cancelled_at AS cancelledAt,
                   recharge.cancel_reason AS cancelReason, recharge.created_at AS createdAt,
                   recharge.row_version AS rowVersion
            FROM dbo.ast_recharge_order recharge
            JOIN dbo.ast_recharge_quote quote ON quote.id = recharge.quote_id
            JOIN dbo.org_store store ON store.id = recharge.store_id
            JOIN dbo.cat_payment_method method ON method.id = recharge.payment_method_id
            """;

    @Select(BALANCE_ACCOUNT_SELECT + " WHERE account.member_id = #{memberId}")
    BalanceAccountRow findBalanceAccount(long memberId);

    @Select(BALANCE_ACCOUNT_SELECT + " WITH (UPDLOCK, HOLDLOCK) WHERE account.member_id = #{memberId}")
    BalanceAccountRow lockBalanceAccount(long memberId);

    @Select("""
            SELECT TOP (#{limit}) ledger.id, account.member_id AS memberId,
                   ledger.ledger_no AS ledgerNo, ledger.transaction_type AS transactionType,
                   ledger.before_balance AS beforeBalance, ledger.change_amount AS changeAmount,
                   ledger.after_balance AS afterBalance, ledger.source_type AS sourceType,
                   ledger.source_id AS sourceId, ledger.store_id AS storeId, store.store_name AS storeName,
                   ledger.occurred_at AS occurredAt, ledger.correlation_id AS correlationId,
                   ledger.reversed_ledger_id AS reversedLedgerId, ledger.note
            FROM dbo.ast_balance_ledger ledger
            JOIN dbo.ast_balance_account account ON account.id = ledger.account_id
            JOIN dbo.org_store store ON store.id = ledger.store_id
            WHERE account.member_id = #{memberId}
            ORDER BY ledger.occurred_at DESC, ledger.id DESC
            """)
    List<BalanceLedgerItem> findBalanceLedgers(@Param("memberId") long memberId, @Param("limit") int limit);

    @Select(POINT_ACCOUNT_SELECT + " WHERE account.member_id = #{memberId}")
    PointAccountRow findPointAccount(long memberId);

    @Select(POINT_ACCOUNT_SELECT + " WITH (UPDLOCK, HOLDLOCK) WHERE account.member_id = #{memberId}")
    PointAccountRow lockPointAccount(long memberId);

    @Select("""
            SELECT TOP (#{limit}) ledger.id, account.member_id AS memberId,
                   ledger.ledger_no AS ledgerNo, ledger.transaction_type AS transactionType,
                   ledger.before_points AS beforePoints, ledger.change_points AS changePoints,
                   ledger.after_points AS afterPoints, ledger.source_type AS sourceType,
                   ledger.source_id AS sourceId, ledger.expired_at AS expiredAt,
                   ledger.occurred_at AS occurredAt, ledger.correlation_id AS correlationId,
                   ledger.reversed_ledger_id AS reversedLedgerId, ledger.note
            FROM dbo.ast_point_ledger ledger
            JOIN dbo.ast_point_account account ON account.id = ledger.account_id
            WHERE account.member_id = #{memberId}
            ORDER BY ledger.occurred_at DESC, ledger.id DESC
            """)
    List<PointLedgerItem> findPointLedgers(@Param("memberId") long memberId, @Param("limit") int limit);

    @Select(value = """
            INSERT INTO dbo.ast_recharge_quote (
                quote_no, member_id, account_id, recharge_amount, gift_amount, credit_amount,
                payment_method_id, expires_at, created_by
            )
            OUTPUT INSERTED.id
            SELECT #{draft.quoteNo}, #{draft.memberId}, account.id,
                   #{draft.rechargeAmount}, #{draft.giftAmount},
                   #{draft.rechargeAmount} + #{draft.giftAmount}, #{draft.paymentMethodId},
                   #{draft.expiresAt}, #{draft.operatorId}
            FROM dbo.ast_balance_account account WHERE account.member_id = #{draft.memberId}
            """, affectData = true)
    long insertRechargeQuote(@Param("draft") RechargeQuoteDraft draft);

    @Select("""
            SELECT quote.id, quote.quote_no AS quoteNo, quote.member_id AS memberId,
                   quote.recharge_amount AS rechargeAmount, quote.gift_amount AS giftAmount,
                   quote.credit_amount AS creditAmount, quote.payment_method_id AS paymentMethodId,
                   method.method_name AS paymentMethodName, quote.expires_at AS expiresAt,
                   CASE WHEN quote.used_at IS NULL THEN CAST(0 AS bit) ELSE CAST(1 AS bit) END AS used
            FROM dbo.ast_recharge_quote quote
            JOIN dbo.cat_payment_method method ON method.id = quote.payment_method_id
            WHERE quote.quote_no = #{quoteNo}
            """)
    RechargeQuote findRechargeQuote(String quoteNo);

    @Update("""
            UPDATE dbo.ast_recharge_quote SET used_at = sysdatetime()
            WHERE id = #{id} AND used_at IS NULL AND expires_at >= sysdatetime()
            """)
    int markRechargeQuoteUsed(long id);

    @Select(value = """
            INSERT INTO dbo.ast_recharge_order (
                recharge_no, quote_id, member_id, account_id, store_id,
                recharge_amount, gift_amount, credit_amount, payment_method_id,
                external_reference, sales_employee_id, idempotency_key,
                created_by, updated_by
            )
            OUTPUT INSERTED.id
            SELECT #{draft.rechargeNo}, quote.id, quote.member_id, quote.account_id, #{draft.storeId},
                   quote.recharge_amount, quote.gift_amount, quote.credit_amount, quote.payment_method_id,
                   #{draft.externalReference}, #{draft.salesEmployeeId}, #{draft.idempotencyKey},
                   #{draft.operatorId}, #{draft.operatorId}
            FROM dbo.ast_recharge_quote quote WHERE quote.id = #{draft.quote.id}
            """, affectData = true)
    long insertRechargeOrder(@Param("draft") RechargeOrderDraft draft);

    @Select(RECHARGE_SELECT + " WHERE recharge.id = #{id}")
    RechargeOrderRow findRechargeOrder(long id);

    @Select(RECHARGE_SELECT + " WHERE recharge.idempotency_key = #{key}")
    RechargeOrderRow findRechargeOrderByIdempotencyKey(String key);

    @Update("""
            UPDATE dbo.ast_balance_account
            SET available_balance = available_balance + #{creditAmount},
                total_recharged = total_recharged + #{rechargeAmount},
                last_transaction_at = #{occurredAt}, updated_at = #{occurredAt}
            WHERE id = #{accountId} AND row_version = #{rowVersion}
            """)
    int creditBalance(
            @Param("accountId") long accountId,
            @Param("creditAmount") BigDecimal creditAmount,
            @Param("rechargeAmount") BigDecimal rechargeAmount,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("rowVersion") byte[] rowVersion);

    @Insert("""
            INSERT INTO dbo.ast_balance_ledger (
                ledger_no, account_id, transaction_type, before_balance, change_amount, after_balance,
                source_type, source_id, store_id, occurred_at, correlation_id, note, created_by
            ) VALUES (
                #{ledgerNo}, #{accountId}, #{transactionType}, #{beforeBalance}, #{changeAmount}, #{afterBalance},
                'RECHARGE', #{sourceId}, #{storeId}, #{occurredAt}, #{correlationId}, #{note}, #{operatorId}
            )
            """)
    void insertBalanceLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("accountId") long accountId,
            @Param("transactionType") String transactionType,
            @Param("beforeBalance") BigDecimal beforeBalance,
            @Param("changeAmount") BigDecimal changeAmount,
            @Param("afterBalance") BigDecimal afterBalance,
            @Param("sourceId") long sourceId,
            @Param("storeId") long storeId,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("correlationId") String correlationId,
            @Param("note") String note,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.ast_recharge_order
            SET status = 'CONFIRMED', confirmed_by = #{operatorId}, confirmed_at = #{occurredAt},
                updated_at = #{occurredAt}, updated_by = #{operatorId}
            WHERE id = #{id} AND status = 'PENDING_CONFIRM' AND row_version = #{rowVersion}
            """)
    int confirmRecharge(
            @Param("id") long id,
            @Param("rowVersion") byte[] rowVersion,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.ast_recharge_order
            SET status = 'CANCELLED', cancelled_by = #{operatorId}, cancelled_at = sysdatetime(),
                cancel_reason = #{reason}, updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND status = 'PENDING_CONFIRM' AND row_version = #{rowVersion}
            """)
    int cancelRecharge(
            @Param("id") long id,
            @Param("rowVersion") byte[] rowVersion,
            @Param("reason") String reason,
            @Param("operatorId") long operatorId);

    @Select("SELECT COUNT(1) FROM dbo.ast_point_ledger WHERE correlation_id = #{correlationId}")
    int countPointLedgerByCorrelation(String correlationId);

    @Update("""
            UPDATE dbo.ast_point_account
            SET available_points = available_points + #{changePoints},
                lifetime_points = lifetime_points + CASE WHEN #{changePoints} > 0 THEN #{changePoints} ELSE 0 END,
                last_transaction_at = #{occurredAt}, updated_at = #{occurredAt}
            WHERE id = #{accountId} AND row_version = #{rowVersion}
              AND available_points + #{changePoints} >= 0
            """)
    int adjustPoints(
            @Param("accountId") long accountId,
            @Param("changePoints") int changePoints,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("rowVersion") byte[] rowVersion);

    @Insert("""
            INSERT INTO dbo.ast_point_ledger (
                ledger_no, account_id, transaction_type, before_points, change_points, after_points,
                source_type, source_id, occurred_at, correlation_id, note, created_by
            ) VALUES (
                #{ledgerNo}, #{accountId}, #{transactionType}, #{beforePoints}, #{changePoints}, #{afterPoints},
                'MANUAL_ADJUSTMENT', #{operatorId}, #{occurredAt}, #{correlationId}, #{note}, #{operatorId}
            )
            """)
    void insertPointLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("accountId") long accountId,
            @Param("transactionType") String transactionType,
            @Param("beforePoints") int beforePoints,
            @Param("changePoints") int changePoints,
            @Param("afterPoints") int afterPoints,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("correlationId") String correlationId,
            @Param("note") String note,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.ast_balance_account
            SET available_balance = available_balance - #{amount},
                last_transaction_at = #{occurredAt}, updated_at = #{occurredAt}
            WHERE id = #{accountId} AND row_version = #{rowVersion}
              AND available_balance >= #{amount}
            """)
    int consumeBalance(
            @Param("accountId") long accountId,
            @Param("amount") BigDecimal amount,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("rowVersion") byte[] rowVersion);

    @Select(value = """
            INSERT INTO dbo.ast_balance_ledger (
                ledger_no, account_id, transaction_type, before_balance, change_amount, after_balance,
                source_type, source_id, store_id, occurred_at, correlation_id, note, created_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{ledgerNo}, #{accountId}, 'CONSUME', #{beforeBalance}, -#{amount}, #{afterBalance},
                'BILL', #{billId}, #{storeId}, #{occurredAt}, CONCAT('bill:', #{billId}, ':balance'),
                #{note}, #{operatorId}
            )
            """, affectData = true)
    long insertBalanceConsumeLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("accountId") long accountId,
            @Param("beforeBalance") BigDecimal beforeBalance,
            @Param("amount") BigDecimal amount,
            @Param("afterBalance") BigDecimal afterBalance,
            @Param("billId") long billId,
            @Param("storeId") long storeId,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("note") String note,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.ast_point_account
            SET available_points = available_points - #{points},
                last_transaction_at = #{occurredAt}, updated_at = #{occurredAt}
            WHERE id = #{accountId} AND row_version = #{rowVersion}
              AND available_points >= #{points}
            """)
    int consumePoints(
            @Param("accountId") long accountId,
            @Param("points") int points,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("rowVersion") byte[] rowVersion);

    @Select(value = """
            INSERT INTO dbo.ast_point_ledger (
                ledger_no, account_id, transaction_type, before_points, change_points, after_points,
                source_type, source_id, occurred_at, correlation_id, note, created_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{ledgerNo}, #{accountId}, 'REDEEM', #{beforePoints}, -#{points}, #{afterPoints},
                'BILL', #{billId}, #{occurredAt}, CONCAT('bill:', #{billId}, ':point'), #{note}, #{operatorId}
            )
            """, affectData = true)
    long insertPointConsumeLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("accountId") long accountId,
            @Param("beforePoints") int beforePoints,
            @Param("points") int points,
            @Param("afterPoints") int afterPoints,
            @Param("billId") long billId,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("note") String note,
            @Param("operatorId") long operatorId);

    @Insert("""
            INSERT INTO dbo.trd_bill_asset_usage (
                bill_id, asset_type, member_id, quantity, amount, asset_ledger_id, display_name, created_by
            ) VALUES (
                #{billId}, #{assetType}, #{memberId}, #{quantity}, #{amount},
                #{ledgerId}, #{displayName}, #{operatorId}
            )
            """)
    void insertAccountAssetUsage(
            @Param("billId") long billId,
            @Param("assetType") String assetType,
            @Param("memberId") long memberId,
            @Param("quantity") BigDecimal quantity,
            @Param("amount") BigDecimal amount,
            @Param("ledgerId") long ledgerId,
            @Param("displayName") String displayName,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.ast_balance_account
            SET available_balance = available_balance + #{amount},
                last_transaction_at = #{occurredAt}, updated_at = #{occurredAt}
            WHERE id = #{accountId} AND row_version = #{rowVersion}
            """)
    int refundBalance(
            @Param("accountId") long accountId,
            @Param("amount") BigDecimal amount,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("rowVersion") byte[] rowVersion);

    @Insert("""
            INSERT INTO dbo.ast_balance_ledger (
                ledger_no, account_id, transaction_type, before_balance, change_amount, after_balance,
                source_type, source_id, store_id, occurred_at, correlation_id,
                reversed_ledger_id, note, created_by
            ) VALUES (
                #{ledgerNo}, #{accountId}, 'REFUND', #{beforeBalance}, #{amount}, #{afterBalance},
                'REVERSAL', #{command.reversalId}, #{command.storeId}, #{occurredAt},
                CONCAT('reversal:', #{command.reversalId}, ':usage:', #{command.usageId}),
                #{command.originalLedgerId}, #{command.note}, #{command.operatorId}
            )
            """)
    void insertBalanceRefundLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("accountId") long accountId,
            @Param("beforeBalance") BigDecimal beforeBalance,
            @Param("amount") BigDecimal amount,
            @Param("afterBalance") BigDecimal afterBalance,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("command") BalanceRefundCommand command);

    @Update("""
            UPDATE dbo.ast_point_account
            SET available_points = available_points + #{points},
                last_transaction_at = #{occurredAt}, updated_at = #{occurredAt}
            WHERE id = #{accountId} AND row_version = #{rowVersion}
            """)
    int refundPoints(
            @Param("accountId") long accountId,
            @Param("points") int points,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("rowVersion") byte[] rowVersion);

    @Insert("""
            INSERT INTO dbo.ast_point_ledger (
                ledger_no, account_id, transaction_type, before_points, change_points, after_points,
                source_type, source_id, occurred_at, correlation_id,
                reversed_ledger_id, note, created_by
            ) VALUES (
                #{ledgerNo}, #{accountId}, 'REFUND', #{beforePoints}, #{points}, #{afterPoints},
                'REVERSAL', #{command.reversalId}, #{occurredAt},
                CONCAT('reversal:', #{command.reversalId}, ':usage:', #{command.usageId}),
                #{command.originalLedgerId}, #{command.note}, #{command.operatorId}
            )
            """)
    void insertPointRefundLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("accountId") long accountId,
            @Param("beforePoints") int beforePoints,
            @Param("points") int points,
            @Param("afterPoints") int afterPoints,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("command") PointRefundCommand command);
}
