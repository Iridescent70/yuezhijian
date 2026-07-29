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
public interface CardMapper {
    String CARD_TYPE_SELECT = """
            SELECT type.id, type.card_type_code AS code, type.card_type_name AS name,
                   type.sale_price AS salePrice, type.list_price AS listPrice,
                   type.total_times AS totalTimes, type.valid_days AS validDays,
                   type.purchase_threshold AS purchaseThreshold, type.instructions,
                   type.auto_remind_days AS autoRemindDays, type.status,
                   type.row_version AS rowVersion
            FROM dbo.cat_card_type type
            """;

    String MEMBER_CARD_SELECT = """
            SELECT card.id, card.card_no AS cardNo, card.member_id AS memberId,
                   card.card_type_id AS cardTypeId, card.card_type_code_snapshot AS cardTypeCode,
                   card.card_type_name_snapshot AS cardTypeName,
                   card.purchase_store_id AS purchaseStoreId, store.store_name AS purchaseStoreName,
                   card.purchase_price AS purchasePrice,
                   COALESCE(balance_total.total_times, 0) AS totalTimes,
                   COALESCE(balance_total.remaining_times, 0) AS remainingTimes,
                   COALESCE(balance_total.frozen_times, 0) AS frozenTimes,
                   card.started_at AS startedAt, card.expires_at AS expiresAt,
                   card.status, card.row_version AS rowVersion
            FROM dbo.ast_member_card card
            JOIN dbo.org_store store ON store.id = card.purchase_store_id
            OUTER APPLY (
                SELECT SUM(balance.total_times) AS total_times,
                       SUM(balance.remaining_times) AS remaining_times,
                       SUM(balance.frozen_times) AS frozen_times
                FROM dbo.ast_member_card_balance balance WHERE balance.member_card_id = card.id
            ) balance_total
            """;

    @Select("""
            <script>
            """ + CARD_TYPE_SELECT + """
            WHERE 1 = 1
            <if test="storeId != null">
              AND EXISTS (
                SELECT 1 FROM dbo.cat_card_type_store cfg
                WHERE cfg.card_type_id = type.id AND cfg.store_id = #{storeId}
                  AND cfg.sale_status = 'ON_SALE'
              )
            </if>
            <if test="keyword != null">
              AND (type.card_type_code LIKE CONCAT('%', #{keyword}, '%')
                   OR type.card_type_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND type.status = #{status}</if>
            ORDER BY type.id DESC
            </script>
            """)
    List<CardTypeRow> searchCardTypes(
            @Param("storeId") Long storeId,
            @Param("keyword") String keyword,
            @Param("status") String status);

    @Select(CARD_TYPE_SELECT + " WHERE type.id = #{id}")
    CardTypeRow findCardType(long id);

    @Select("SELECT COUNT(1) FROM dbo.cat_card_type WHERE card_type_code = #{code}")
    int countCardTypeCode(String code);

    @Select("""
            SELECT store_id FROM dbo.cat_card_type_store
            WHERE card_type_id = #{cardTypeId} AND sale_status = 'ON_SALE'
            ORDER BY sort_no, id
            """)
    List<Long> findCardTypeStores(long cardTypeId);

    @Select("""
            SELECT rule.service_id AS serviceId, service.service_code AS serviceCode,
                   service.service_name AS serviceName, rule.included_times AS includedTimes,
                   rule.deduct_times AS deductTimes, rule.priority
            FROM dbo.cat_card_service_rule rule
            JOIN dbo.cat_service service ON service.id = rule.service_id
            WHERE rule.card_type_id = #{cardTypeId}
            ORDER BY rule.priority, rule.id
            """)
    List<CardServiceRule> findCardServiceRules(long cardTypeId);

    @Select(value = """
            INSERT INTO dbo.cat_card_type (
                card_type_code, card_type_name, category_id, sale_price, list_price,
                total_times, valid_days, purchase_threshold, instructions, auto_remind_days,
                created_by, updated_by
            )
            OUTPUT INSERTED.id
            SELECT #{draft.code}, #{draft.name}, category.id, #{draft.salePrice}, #{draft.listPrice},
                   #{draft.totalTimes}, #{draft.validDays}, #{draft.purchaseThreshold},
                   #{draft.instructions}, #{draft.autoRemindDays}, #{draft.operatorId}, #{draft.operatorId}
            FROM dbo.cat_category category
            WHERE category.category_type = 'CARD' AND category.category_code = 'SERVICE_CARD'
            """, affectData = true)
    long insertCardType(@Param("draft") CardTypeDraft draft);

    @Insert("""
            INSERT INTO dbo.cat_card_type_store (card_type_id, store_id, sort_no)
            VALUES (#{cardTypeId}, #{storeId}, #{sortNo})
            """)
    void insertCardTypeStore(
            @Param("cardTypeId") long cardTypeId,
            @Param("storeId") long storeId,
            @Param("sortNo") int sortNo);

    @Insert("""
            INSERT INTO dbo.cat_card_service_rule (
                card_type_id, service_id, included_times, deduct_times, priority
            ) VALUES (#{cardTypeId}, #{rule.serviceId}, #{rule.includedTimes}, #{rule.deductTimes}, #{rule.priority})
            """)
    void insertCardServiceRule(@Param("cardTypeId") long cardTypeId, @Param("rule") CardServiceRule rule);

    @Select("""
            SELECT id, order_no AS orderNo, total_amount AS totalAmount
            FROM dbo.ast_card_sale_order WHERE idempotency_key = #{key}
            """)
    CardSaleRow findSaleByIdempotencyKey(String key);

    @Select("SELECT sale_employee_id FROM dbo.ast_member_card WHERE id = #{memberCardId}")
    Long findCardSaleEmployeeId(long memberCardId);

    @Select(value = """
            INSERT INTO dbo.ast_card_sale_order (
                order_no, member_id, card_type_id, store_id, quantity, unit_price, total_amount,
                payment_method_id, external_reference, sales_employee_id, idempotency_key, created_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{draft.orderNo}, #{draft.memberId}, #{draft.cardType.id}, #{draft.storeId}, #{draft.quantity},
                #{draft.cardType.salePrice}, #{draft.cardType.salePrice} * #{draft.quantity},
                #{draft.paymentMethodId}, #{draft.externalReference}, #{draft.salesEmployeeId},
                #{draft.idempotencyKey}, #{draft.operatorId}
            )
            """, affectData = true)
    long insertSaleOrder(@Param("draft") PurchaseMemberCardDraft draft);

    @Select(value = """
            INSERT INTO dbo.ast_member_card (
                card_no, member_id, card_type_id, card_type_code_snapshot, card_type_name_snapshot,
                source_order_id, purchase_store_id, sale_employee_id, purchase_price,
                started_at, expires_at, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{cardNo}, #{draft.memberId}, #{draft.cardType.id}, #{draft.cardType.code}, #{draft.cardType.name},
                #{orderId}, #{draft.storeId}, #{draft.salesEmployeeId}, #{draft.cardType.salePrice},
                #{draft.startedAt}, #{expiresAt}, #{draft.operatorId}, #{draft.operatorId}
            )
            """, affectData = true)
    long insertMemberCard(
            @Param("orderId") long orderId,
            @Param("cardNo") String cardNo,
            @Param("draft") PurchaseMemberCardDraft draft,
            @Param("expiresAt") LocalDateTime expiresAt);

    @Select(value = """
            INSERT INTO dbo.ast_member_card_balance (
                member_card_id, service_id, total_times, remaining_times, deduct_times
            )
            OUTPUT INSERTED.id
            VALUES (
                #{memberCardId}, #{rule.serviceId}, #{rule.includedTimes}, #{rule.includedTimes}, #{rule.deductTimes}
            )
            """, affectData = true)
    long insertMemberCardBalance(
            @Param("memberCardId") long memberCardId,
            @Param("rule") CardServiceRule rule);

    @Insert("""
            INSERT INTO dbo.ast_member_card_ledger (
                ledger_no, member_card_id, service_id, transaction_type,
                before_times, change_times, after_times, value_amount,
                source_type, source_id, occurred_at, correlation_id, note, created_by
            ) VALUES (
                #{ledgerNo}, #{memberCardId}, #{rule.serviceId}, 'PURCHASE',
                0, #{rule.includedTimes}, #{rule.includedTimes}, #{valueAmount},
                'CARD_SALE', #{orderId}, sysdatetime(), #{correlationId}, N'售卡入账', #{operatorId}
            )
            """)
    void insertPurchaseLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("memberCardId") long memberCardId,
            @Param("rule") CardServiceRule rule,
            @Param("valueAmount") BigDecimal valueAmount,
            @Param("orderId") long orderId,
            @Param("correlationId") String correlationId,
            @Param("operatorId") long operatorId);

    @Select("""
            <script>
            """ + MEMBER_CARD_SELECT + """
            WHERE card.member_id = #{memberId}
            <if test="status != null">AND card.status = #{status}</if>
            ORDER BY card.expires_at, card.id
            </script>
            """)
    List<MemberCardRow> findMemberCards(@Param("memberId") long memberId, @Param("status") String status);

    @Select(MEMBER_CARD_SELECT + " WHERE card.id = #{id}")
    MemberCardRow findMemberCard(long id);

    @Select(MEMBER_CARD_SELECT + " WHERE card.source_order_id = #{orderId} ORDER BY card.id")
    List<MemberCardRow> findMemberCardsByOrder(long orderId);

    @Select("""
            SELECT balance.id, balance.member_card_id AS memberCardId, balance.service_id AS serviceId,
                   service.service_code AS serviceCode, service.service_name AS serviceName,
                   balance.total_times AS totalTimes, balance.remaining_times AS remainingTimes,
                   balance.frozen_times AS frozenTimes, balance.deduct_times AS deductTimes,
                   balance.row_version AS rowVersion
            FROM dbo.ast_member_card_balance balance
            JOIN dbo.cat_service service ON service.id = balance.service_id
            WHERE balance.member_card_id = #{memberCardId}
            ORDER BY balance.id
            """)
    List<MemberCardBalanceRow> findMemberCardBalances(long memberCardId);

    @Select("""
            SELECT ledger.id, ledger.ledger_no AS ledgerNo, ledger.service_id AS serviceId,
                   service.service_name AS serviceName, ledger.transaction_type AS transactionType,
                   ledger.before_times AS beforeTimes, ledger.change_times AS changeTimes,
                   ledger.after_times AS afterTimes, ledger.value_amount AS valueAmount,
                   ledger.source_type AS sourceType, ledger.source_id AS sourceId,
                   ledger.occurred_at AS occurredAt, ledger.correlation_id AS correlationId,
                   ledger.reversed_ledger_id AS reversedLedgerId, ledger.note
            FROM dbo.ast_member_card_ledger ledger
            JOIN dbo.cat_service service ON service.id = ledger.service_id
            WHERE ledger.member_card_id = #{memberCardId}
            ORDER BY ledger.occurred_at DESC, ledger.id DESC
            """)
    List<MemberCardLedgerItem> findMemberCardLedgers(long memberCardId);

    @Select("""
            SELECT balance.id, balance.member_card_id AS memberCardId, balance.service_id AS serviceId,
                   service.service_code AS serviceCode, service.service_name AS serviceName,
                   balance.total_times AS totalTimes, balance.remaining_times AS remainingTimes,
                   balance.frozen_times AS frozenTimes, balance.deduct_times AS deductTimes,
                   balance.row_version AS rowVersion
            FROM dbo.ast_member_card_balance balance WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.cat_service service ON service.id = balance.service_id
            WHERE balance.id = #{id}
            """)
    MemberCardBalanceRow lockMemberCardBalance(long id);

    @Update("""
            UPDATE dbo.ast_member_card_balance
            SET remaining_times = remaining_times - #{times}, updated_at = sysdatetime()
            WHERE id = #{id} AND row_version = #{rowVersion} AND remaining_times >= #{times}
            """)
    int consumeCardBalance(
            @Param("id") long id,
            @Param("times") BigDecimal times,
            @Param("rowVersion") byte[] rowVersion);

    @Select(value = """
            INSERT INTO dbo.ast_member_card_ledger (
                ledger_no, member_card_id, service_id, transaction_type,
                before_times, change_times, after_times, value_amount,
                source_type, source_id, source_line_id, occurred_at, correlation_id, note, created_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{ledgerNo}, #{command.memberCardId}, #{command.serviceId}, 'CONSUME',
                #{beforeTimes}, -#{command.times}, #{afterTimes}, #{command.amount},
                'BILL', #{command.billId}, #{command.billLineId}, sysdatetime(),
                CONCAT('bill:', #{command.billId}, ':line:', #{command.billLineId}),
                #{command.displayName}, #{command.operatorId}
            )
            """, affectData = true)
    long insertCardConsumeLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("command") CardSettlementConsumption command,
            @Param("beforeTimes") BigDecimal beforeTimes,
            @Param("afterTimes") BigDecimal afterTimes);

    @Update("""
            UPDATE dbo.ast_member_card
            SET status = CASE WHEN NOT EXISTS (
                    SELECT 1 FROM dbo.ast_member_card_balance balance
                    WHERE balance.member_card_id = #{memberCardId} AND balance.remaining_times > 0
                ) THEN 'EXHAUSTED' ELSE status END,
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{memberCardId} AND status = 'ACTIVE'
            """)
    void refreshMemberCardStatus(
            @Param("memberCardId") long memberCardId,
            @Param("operatorId") long operatorId);

    @Insert("""
            INSERT INTO dbo.trd_bill_asset_usage (
                bill_id, asset_type, member_id, member_card_id, member_card_balance_id,
                bill_line_id, service_id, quantity, amount, asset_ledger_id, display_name, created_by
            ) VALUES (
                #{command.billId}, 'CARD', #{command.memberId}, #{command.memberCardId},
                #{command.memberCardBalanceId}, #{command.billLineId}, #{command.serviceId},
                #{command.times}, #{command.amount}, #{ledgerId}, #{command.displayName}, #{command.operatorId}
            )
            """)
    void insertCardAssetUsage(
            @Param("command") CardSettlementConsumption command,
            @Param("ledgerId") long ledgerId);

    @Update("""
            UPDATE dbo.ast_member_card_balance
            SET remaining_times = remaining_times + #{times}, updated_at = sysdatetime()
            WHERE id = #{id} AND row_version = #{rowVersion}
              AND remaining_times + #{times} <= total_times - frozen_times
            """)
    int refundCardBalance(
            @Param("id") long id,
            @Param("times") BigDecimal times,
            @Param("rowVersion") byte[] rowVersion);

    @Insert("""
            INSERT INTO dbo.ast_member_card_ledger (
                ledger_no, member_card_id, service_id, transaction_type,
                before_times, change_times, after_times, value_amount,
                source_type, source_id, source_line_id, occurred_at, correlation_id,
                reversed_ledger_id, note, created_by
            ) VALUES (
                #{ledgerNo}, #{command.memberCardId}, #{command.serviceId}, 'REFUND',
                #{beforeTimes}, #{command.times}, #{afterTimes}, #{command.amount},
                'REVERSAL', #{command.reversalId}, NULL, sysdatetime(),
                CONCAT('reversal:', #{command.reversalId}, ':usage:', #{command.usageId}),
                #{command.originalLedgerId}, #{command.note}, #{command.operatorId}
            )
            """)
    void insertCardRefundLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("command") CardRefundCommand command,
            @Param("beforeTimes") BigDecimal beforeTimes,
            @Param("afterTimes") BigDecimal afterTimes);

    @Update("""
            UPDATE dbo.ast_member_card
            SET status = CASE
                    WHEN status = 'EXHAUSTED' AND expires_at >= sysdatetime() THEN 'ACTIVE'
                    ELSE status
                END,
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{memberCardId}
            """)
    void restoreMemberCardStatus(
            @Param("memberCardId") long memberCardId,
            @Param("operatorId") long operatorId);

    @Select("""
            SELECT ledger.id AS cardLedgerId, ledger.source_id AS billId, bill.bill_no AS billNo,
                   ledger.service_id AS serviceId, service.service_name AS serviceName,
                   ledger.occurred_at AS consumedAt, line.original_amount AS originalAmount
            FROM dbo.ast_member_card_ledger ledger
            JOIN dbo.trd_bill bill ON bill.id = ledger.source_id
            JOIN dbo.trd_bill_line line ON line.id = ledger.source_line_id
            JOIN dbo.cat_service service ON service.id = ledger.service_id
            WHERE ledger.member_card_id = #{memberCardId}
              AND ledger.transaction_type = 'CONSUME'
              AND NOT EXISTS (
                  SELECT 1 FROM dbo.ast_member_card_ledger reversed
                  WHERE reversed.reversed_ledger_id = ledger.id
              )
            ORDER BY ledger.occurred_at, ledger.id
            """)
    List<CardConsumptionRepriceItem> findConsumptionRepriceItems(long memberCardId);

    @Select(value = """
            INSERT INTO dbo.ast_card_exchange_quote (
                quote_no, old_card_id, target_card_type_id, old_remaining_times,
                old_remaining_value, new_card_value, difference_amount,
                old_card_row_version, target_card_type_row_version, expires_at, created_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{quoteNo}, #{oldCard.id}, #{targetCardType.id}, #{oldRemainingTimes},
                #{oldRemainingValue}, #{targetCardType.salePrice}, #{differenceAmount},
                CONVERT(binary(8), #{oldCard.version}, 1),
                CONVERT(binary(8), #{targetCardType.version}, 1), #{expiresAt}, #{operatorId}
            )
            """, affectData = true)
    long insertExchangeQuote(CardExchangeQuoteDraft draft);

    @Select("""
            SELECT quote.id, quote.quote_no AS quoteNo, quote.old_card_id AS oldCardId,
                   old_card.card_no AS oldCardNo,
                   old_card.card_type_name_snapshot AS oldCardTypeName,
                   quote.target_card_type_id AS targetCardTypeId,
                   target.card_type_name AS targetCardTypeName,
                   CONVERT(varchar(18), quote.target_card_type_row_version, 1) AS targetCardTypeVersion,
                   quote.old_remaining_times AS oldRemainingTimes,
                   quote.old_remaining_value AS oldRemainingValue,
                   quote.new_card_value AS newCardValue,
                   quote.difference_amount AS differenceAmount,
                   CONVERT(varchar(18), quote.old_card_row_version, 1) AS oldCardVersion,
                   quote.expires_at AS expiresAt,
                   CASE WHEN quote.used_at IS NULL THEN CAST(0 AS bit) ELSE CAST(1 AS bit) END AS used
            FROM dbo.ast_card_exchange_quote quote
            JOIN dbo.ast_member_card old_card ON old_card.id = quote.old_card_id
            JOIN dbo.cat_card_type target ON target.id = quote.target_card_type_id
            WHERE quote.quote_no = #{quoteNo}
            """)
    CardExchangeQuote findExchangeQuote(String quoteNo);

    @Update("""
            UPDATE dbo.ast_card_exchange_quote SET used_at = sysdatetime()
            WHERE id = #{id} AND used_at IS NULL AND expires_at >= sysdatetime()
              AND old_card_row_version = CONVERT(binary(8), #{oldCardVersion}, 1)
            """)
    int markExchangeQuoteUsed(@Param("id") long id, @Param("oldCardVersion") String oldCardVersion);

    @Select("""
            SELECT balance.id, balance.member_card_id AS memberCardId, balance.service_id AS serviceId,
                   service.service_code AS serviceCode, service.service_name AS serviceName,
                   balance.total_times AS totalTimes, balance.remaining_times AS remainingTimes,
                   balance.frozen_times AS frozenTimes, balance.deduct_times AS deductTimes,
                   balance.row_version AS rowVersion
            FROM dbo.ast_member_card_balance balance WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.cat_service service ON service.id = balance.service_id
            WHERE balance.member_card_id = #{memberCardId}
            ORDER BY balance.id
            """)
    List<MemberCardBalanceRow> lockMemberCardBalances(long memberCardId);

    @Select(value = """
            INSERT INTO dbo.ast_member_card (
                card_no, member_id, card_type_id, card_type_code_snapshot, card_type_name_snapshot,
                source_order_id, purchase_store_id, sale_employee_id, purchase_price,
                started_at, expires_at, original_card_id, created_by, updated_by
            )
            OUTPUT INSERTED.id
            SELECT
                #{cardNo}, #{command.memberId}, #{command.targetCardType.id},
                #{command.targetCardType.code}, #{command.targetCardType.name}, NULL,
                #{command.storeId}, #{command.employeeId}, #{command.targetCardType.salePrice},
                #{command.startedAt}, DATEADD(day, #{command.targetCardType.validDays}, #{command.startedAt}),
                #{command.quote.oldCardId}, #{command.operatorId}, #{command.operatorId}
            FROM dbo.cat_card_type target WITH (UPDLOCK, HOLDLOCK)
            WHERE target.id = #{command.targetCardType.id}
              AND target.row_version = CONVERT(binary(8), #{command.quote.targetCardTypeVersion}, 1)
            """, affectData = true)
    Long insertExchangeMemberCard(@Param("cardNo") String cardNo, @Param("command") CardExchangeCommand command);

    @Select(value = """
            INSERT INTO dbo.ast_card_exchange (
                exchange_no, quote_id, old_card_id, new_card_id, member_id,
                old_remaining_value, new_card_value, difference_amount,
                handled_store_id, handled_employee_id, idempotency_key, created_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{command.exchangeNo}, #{command.quote.id}, #{command.quote.oldCardId}, #{newCardId},
                #{command.memberId}, #{command.quote.oldRemainingValue}, #{command.quote.newCardValue},
                #{command.quote.differenceAmount}, #{command.storeId}, #{command.employeeId},
                #{command.idempotencyKey}, #{command.operatorId}
            )
            """, affectData = true)
    long insertExchange(@Param("command") CardExchangeCommand command, @Param("newCardId") long newCardId);

    @Update("""
            UPDATE dbo.ast_member_card_balance
            SET remaining_times = 0, updated_at = sysdatetime()
            WHERE id = #{id} AND row_version = #{rowVersion} AND frozen_times = 0
            """)
    int clearCardBalance(@Param("id") long id, @Param("rowVersion") byte[] rowVersion);

    @Insert("""
            INSERT INTO dbo.ast_member_card_ledger (
                ledger_no, member_card_id, service_id, transaction_type,
                before_times, change_times, after_times, value_amount,
                source_type, source_id, occurred_at, correlation_id, note, created_by
            ) VALUES (
                #{ledgerNo}, #{balance.memberCardId}, #{balance.serviceId}, 'EXCHANGE_OUT',
                #{balance.remainingTimes}, -#{balance.remainingTimes}, 0, #{valueAmount},
                'CARD_EXCHANGE', #{exchangeId}, sysdatetime(),
                CONCAT('card-exchange:', #{exchangeId}, ':out:', #{balance.id}),
                N'换卡转出剩余次数', #{operatorId}
            )
            """)
    void insertExchangeOutLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("exchangeId") long exchangeId,
            @Param("balance") MemberCardBalanceRow balance,
            @Param("valueAmount") BigDecimal valueAmount,
            @Param("operatorId") long operatorId);

    @Insert("""
            INSERT INTO dbo.ast_member_card_ledger (
                ledger_no, member_card_id, service_id, transaction_type,
                before_times, change_times, after_times, value_amount,
                source_type, source_id, occurred_at, correlation_id, note, created_by
            ) VALUES (
                #{ledgerNo}, #{newCardId}, #{rule.serviceId}, 'EXCHANGE_IN',
                0, #{rule.includedTimes}, #{rule.includedTimes}, #{valueAmount},
                'CARD_EXCHANGE', #{exchangeId}, sysdatetime(),
                CONCAT('card-exchange:', #{exchangeId}, ':in:', #{rule.serviceId}),
                N'换卡转入新卡次数', #{operatorId}
            )
            """)
    void insertExchangeInLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("exchangeId") long exchangeId,
            @Param("newCardId") long newCardId,
            @Param("rule") CardServiceRule rule,
            @Param("valueAmount") BigDecimal valueAmount,
            @Param("operatorId") long operatorId);

    @Insert("""
            INSERT INTO dbo.ast_card_exchange_payment (
                exchange_id, payment_method_id, amount, external_reference, sort_no
            ) VALUES (#{exchangeId}, #{payment.paymentMethodId}, #{payment.amount},
                      #{payment.externalReference}, #{sortNo})
            """)
    void insertExchangePayment(
            @Param("exchangeId") long exchangeId,
            @Param("payment") CardExchangePayment payment,
            @Param("sortNo") int sortNo);

    @Update("""
            UPDATE dbo.ast_member_card
            SET status = 'EXCHANGED', updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{cardId} AND status = 'ACTIVE'
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int markCardExchanged(
            @Param("cardId") long cardId,
            @Param("version") String version,
            @Param("operatorId") long operatorId);

    @Select("""
            SELECT exchange.id, exchange.exchange_no AS exchangeNo,
                   exchange.old_card_id AS oldCardId, exchange.new_card_id AS newCardId,
                   exchange.old_remaining_value AS oldRemainingValue,
                   exchange.new_card_value AS newCardValue,
                   exchange.difference_amount AS differenceAmount,
                   exchange.executed_at AS executedAt
            FROM dbo.ast_card_exchange exchange
            WHERE exchange.idempotency_key = #{key}
            """)
    CardExchangeRow findExchangeByIdempotencyKey(String key);

    @Select("""
            SELECT payment.payment_method_id AS paymentMethodId, method.method_name AS paymentMethodName,
                   payment.amount, payment.external_reference AS externalReference
            FROM dbo.ast_card_exchange_payment payment
            JOIN dbo.cat_payment_method method ON method.id = payment.payment_method_id
            WHERE payment.exchange_id = #{exchangeId}
            ORDER BY payment.sort_no
            """)
    List<CardExchangePayment> findExchangePayments(long exchangeId);

    @Select(value = """
            INSERT INTO dbo.ast_member_card (
                card_no, member_id, card_type_id, card_type_code_snapshot, card_type_name_snapshot,
                source_order_id, purchase_store_id, sale_employee_id, purchase_price,
                started_at, expires_at, transfer_from_card_id, created_by, updated_by
            )
            OUTPUT INSERTED.id
            SELECT
                #{cardNo}, recipient.id, #{command.sourceCard.cardTypeId},
                #{command.sourceCard.cardTypeCode}, #{command.sourceCard.cardTypeName}, NULL,
                #{command.sourceCard.purchaseStoreId}, NULL, #{command.remainingValue},
                #{command.executedAt}, #{command.newExpiresAt}, #{command.sourceCard.id},
                #{command.operatorId}, #{command.operatorId}
            FROM dbo.mem_member recipient WITH (UPDLOCK, HOLDLOCK)
            WHERE recipient.id = #{command.recipientMemberId} AND recipient.status = 'ACTIVE'
            """, affectData = true)
    Long insertTransferMemberCard(@Param("cardNo") String cardNo, @Param("command") CardTransferCommand command);

    @Select(value = """
            INSERT INTO dbo.ast_card_transfer (
                transfer_no, source_card_id, target_card_id, source_member_id, recipient_member_id,
                remaining_times, remaining_value, old_expires_at, new_expires_at, reason,
                handled_store_id, handled_employee_id, idempotency_key, executed_at, created_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{command.transferNo}, #{command.sourceCard.id}, #{targetCardId},
                #{command.sourceCard.memberId}, #{command.recipientMemberId},
                #{command.remainingTimes}, #{command.remainingValue}, #{command.sourceCard.expiresAt},
                #{command.newExpiresAt}, #{command.reason}, #{command.storeId}, #{command.employeeId},
                #{command.idempotencyKey}, #{command.executedAt}, #{command.operatorId}
            )
            """, affectData = true)
    long insertCardTransfer(@Param("command") CardTransferCommand command, @Param("targetCardId") long targetCardId);

    @Insert("""
            INSERT INTO dbo.ast_member_card_balance (
                member_card_id, service_id, total_times, remaining_times, frozen_times, deduct_times
            ) VALUES (
                #{targetCardId}, #{balance.serviceId}, #{balance.remainingTimes},
                #{balance.remainingTimes}, 0, #{balance.deductTimes}
            )
            """)
    void insertTransferBalance(
            @Param("targetCardId") long targetCardId,
            @Param("balance") MemberCardBalanceRow balance);

    @Insert("""
            INSERT INTO dbo.ast_member_card_ledger (
                ledger_no, member_card_id, service_id, transaction_type,
                before_times, change_times, after_times, value_amount,
                source_type, source_id, occurred_at, correlation_id, note, created_by
            ) VALUES (
                #{ledgerNo}, #{balance.memberCardId}, #{balance.serviceId}, 'TRANSFER_OUT',
                #{balance.remainingTimes}, -#{balance.remainingTimes}, 0, #{valueAmount},
                'CARD_TRANSFER', #{transferId}, #{executedAt},
                CONCAT('card-transfer:', #{transferId}, ':out:', #{balance.id}),
                N'次卡转赠转出', #{operatorId}
            )
            """)
    void insertTransferOutLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("transferId") long transferId,
            @Param("balance") MemberCardBalanceRow balance,
            @Param("valueAmount") BigDecimal valueAmount,
            @Param("executedAt") java.time.LocalDateTime executedAt,
            @Param("operatorId") long operatorId);

    @Insert("""
            INSERT INTO dbo.ast_member_card_ledger (
                ledger_no, member_card_id, service_id, transaction_type,
                before_times, change_times, after_times, value_amount,
                source_type, source_id, occurred_at, correlation_id, note, created_by
            ) VALUES (
                #{ledgerNo}, #{targetCardId}, #{balance.serviceId}, 'TRANSFER_IN',
                0, #{balance.remainingTimes}, #{balance.remainingTimes}, #{valueAmount},
                'CARD_TRANSFER', #{transferId}, #{executedAt},
                CONCAT('card-transfer:', #{transferId}, ':in:', #{balance.serviceId}),
                N'次卡转赠转入', #{operatorId}
            )
            """)
    void insertTransferInLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("transferId") long transferId,
            @Param("targetCardId") long targetCardId,
            @Param("balance") MemberCardBalanceRow balance,
            @Param("valueAmount") BigDecimal valueAmount,
            @Param("executedAt") java.time.LocalDateTime executedAt,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.ast_member_card
            SET status = 'TRANSFERRED', updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{cardId} AND status = 'ACTIVE'
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int markCardTransferred(
            @Param("cardId") long cardId,
            @Param("version") String version,
            @Param("operatorId") long operatorId);

    @Select("""
            SELECT transfer.id, transfer.transfer_no AS transferNo,
                   transfer.source_card_id AS sourceCardId, transfer.target_card_id AS targetCardId,
                   transfer.source_member_id AS sourceMemberId,
                   transfer.recipient_member_id AS recipientMemberId,
                   recipient.full_name AS recipientMemberName,
                   transfer.remaining_times AS remainingTimes,
                   transfer.remaining_value AS remainingValue,
                   transfer.old_expires_at AS oldExpiresAt,
                   transfer.new_expires_at AS newExpiresAt,
                   transfer.reason, transfer.executed_at AS executedAt
            FROM dbo.ast_card_transfer transfer
            JOIN dbo.mem_member recipient ON recipient.id = transfer.recipient_member_id
            WHERE transfer.idempotency_key = #{key}
            """)
    CardTransferRow findTransferByIdempotencyKey(String key);

    @Select(value = """
            INSERT INTO dbo.ast_card_refund_quote (
                quote_no, member_card_id, original_amount, consumed_reprice_amount,
                fee_amount, refund_amount, card_row_version, expires_at, created_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{quoteNo}, #{card.id}, #{originalAmount}, #{consumedRepriceAmount},
                #{feeAmount}, #{refundAmount}, CONVERT(binary(8), #{card.version}, 1),
                #{expiresAt}, #{operatorId}
            )
            """, affectData = true)
    long insertRefundQuote(CardRefundQuoteDraft draft);

    @Insert("""
            INSERT INTO dbo.ast_card_refund_quote_item (
                quote_id, card_ledger_id, bill_id, bill_no_snapshot, service_id,
                service_name_snapshot, consumed_at, original_amount, sort_no
            ) VALUES (
                #{quoteId}, #{item.cardLedgerId}, #{item.billId}, #{item.billNo}, #{item.serviceId},
                #{item.serviceName}, #{item.consumedAt}, #{item.originalAmount}, #{sortNo}
            )
            """)
    void insertRefundQuoteItem(
            @Param("quoteId") long quoteId,
            @Param("item") CardConsumptionRepriceItem item,
            @Param("sortNo") int sortNo);

    @Select("""
            SELECT quote.id, quote.quote_no AS quoteNo, quote.member_card_id AS memberCardId,
                   card.card_no AS cardNo, card.card_type_name_snapshot AS cardTypeName,
                   card.member_id AS memberId, quote.original_amount AS originalAmount,
                   quote.consumed_reprice_amount AS consumedRepriceAmount,
                   quote.fee_amount AS feeAmount, quote.refund_amount AS refundAmount,
                   CONVERT(varchar(18), quote.card_row_version, 1) AS cardVersion,
                   quote.expires_at AS expiresAt,
                   CASE WHEN quote.used_at IS NULL THEN CAST(0 AS bit) ELSE CAST(1 AS bit) END AS used
            FROM dbo.ast_card_refund_quote quote
            JOIN dbo.ast_member_card card ON card.id = quote.member_card_id
            WHERE quote.quote_no = #{quoteNo}
            """)
    CardRefundQuoteRow findRefundQuote(String quoteNo);

    @Select("""
            SELECT item.card_ledger_id AS cardLedgerId, item.bill_id AS billId,
                   item.bill_no_snapshot AS billNo, item.service_id AS serviceId,
                   item.service_name_snapshot AS serviceName, item.consumed_at AS consumedAt,
                   item.original_amount AS originalAmount
            FROM dbo.ast_card_refund_quote_item item
            WHERE item.quote_id = #{quoteId}
            ORDER BY item.sort_no
            """)
    List<CardConsumptionRepriceItem> findRefundQuoteItems(long quoteId);

    @Update("""
            UPDATE dbo.ast_card_refund_quote SET used_at = sysdatetime()
            WHERE id = #{id} AND used_at IS NULL AND expires_at >= sysdatetime()
              AND card_row_version = CONVERT(binary(8), #{cardVersion}, 1)
            """)
    int markRefundQuoteUsed(
            @Param("id") long id,
            @Param("cardVersion") String cardVersion);

    @Update("""
            UPDATE dbo.ast_member_card
            SET status = 'FROZEN', updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{cardId} AND status = 'ACTIVE'
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int freezeCardForRefund(
            @Param("cardId") long cardId,
            @Param("version") String version,
            @Param("operatorId") long operatorId);

    @Select(value = """
            INSERT INTO dbo.ast_card_refund_request (
                request_no, quote_id, member_card_id, member_id,
                original_amount, consumed_reprice_amount, fee_amount, refund_amount,
                refund_method_id, refund_method_name_snapshot, refund_method_requires_reference,
                handled_store_id, handled_employee_id, reason, card_row_version,
                request_idempotency_key, requested_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{submission.requestNo}, #{submission.quote.id}, #{submission.quote.memberCardId},
                #{submission.quote.memberId}, #{submission.quote.originalAmount},
                #{submission.quote.consumedRepriceAmount}, #{submission.quote.feeAmount},
                #{submission.quote.refundAmount}, #{submission.refundMethodId},
                #{submission.refundMethodName}, #{submission.refundMethodRequiresReference},
                #{submission.storeId}, #{submission.employeeId}, #{submission.reason},
                CONVERT(binary(8), #{frozenCardVersion}, 1), #{submission.idempotencyKey},
                #{submission.operatorId}
            )
            """, affectData = true)
    long insertRefundRequest(
            @Param("submission") CardRefundSubmission submission,
            @Param("frozenCardVersion") String frozenCardVersion);

    String REFUND_SUMMARY_SELECT = """
            SELECT request.id, request.quote_id AS quoteId, request.request_no AS requestNo,
                   request.member_card_id AS memberCardId, card.card_no AS cardNo,
                   card.card_type_name_snapshot AS cardTypeName, request.member_id AS memberId,
                   member.full_name AS memberName, store.store_name AS storeName,
                   request.original_amount AS originalAmount,
                   request.consumed_reprice_amount AS consumedRepriceAmount,
                   request.fee_amount AS feeAmount, request.refund_amount AS refundAmount,
                   request.refund_method_id AS refundMethodId,
                   request.refund_method_name_snapshot AS refundMethodName,
                   request.refund_method_requires_reference AS refundMethodRequiresReference,
                   request.status, request.commission_adjustment_status AS commissionAdjustmentStatus,
                   request.reason, request.requested_at AS requestedAt, request.requested_by AS requestedBy,
                   request.reviewed_at AS reviewedAt, request.reviewed_by AS reviewedBy,
                   request.review_comment AS reviewComment, request.executed_at AS executedAt,
                   CONVERT(varchar(18), request.card_row_version, 1) AS cardVersion,
                   CONVERT(varchar(18), request.row_version, 1) AS version
            FROM dbo.ast_card_refund_request request
            JOIN dbo.ast_member_card card ON card.id = request.member_card_id
            JOIN dbo.mem_member member ON member.id = request.member_id
            JOIN dbo.org_store store ON store.id = request.handled_store_id
            """;

    @Select("""
            <script>
            """ + REFUND_SUMMARY_SELECT + """
            WHERE 1 = 1
            <if test="status != null">AND request.status = #{status}</if>
            ORDER BY request.requested_at DESC, request.id DESC
            </script>
            """)
    List<CardRefundRequestSummary> findRefundRequests(@Param("status") String status);

    @Select(REFUND_SUMMARY_SELECT + " WHERE request.id = #{id}")
    CardRefundRequestSummary findRefundRequest(long id);

    @Select(REFUND_SUMMARY_SELECT + " WHERE request.request_idempotency_key = #{key}")
    CardRefundRequestSummary findRefundRequestByRequestKey(String key);

    @Select(REFUND_SUMMARY_SELECT + " WHERE request.execution_idempotency_key = #{key}")
    CardRefundRequestSummary findRefundRequestByExecutionKey(String key);

    @Select(REFUND_SUMMARY_SELECT + " WHERE request.member_card_id = #{cardId} AND request.status IN ('SUBMITTED','APPROVED','EXECUTED')")
    CardRefundRequestSummary findActiveRefundRequest(long cardId);

    @Select("""
            SELECT payment.payment_method_id AS paymentMethodId,
                   method.method_name AS paymentMethodName, payment.refund_amount AS amount,
                   payment.refund_status AS status,
                   payment.external_refund_reference AS externalRefundReference,
                   payment.completed_at AS completedAt
            FROM dbo.ast_card_refund_payment payment
            JOIN dbo.cat_payment_method method ON method.id = payment.payment_method_id
            WHERE payment.request_id = #{requestId}
            """)
    CardRefundPayment findCardRefundPayment(long requestId);

    @Update("""
            UPDATE dbo.ast_card_refund_request
            SET status = #{status}, reviewed_at = sysdatetime(), reviewed_by = #{operatorId},
                review_comment = #{comment}
            WHERE id = #{id} AND status = 'SUBMITTED'
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int reviewCardRefund(
            @Param("id") long id,
            @Param("status") String status,
            @Param("comment") String comment,
            @Param("version") String version,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.ast_member_card
            SET status = 'ACTIVE', updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{cardId} AND status = 'FROZEN'
              AND row_version = CONVERT(binary(8), #{cardVersion}, 1)
            """)
    int unfreezeRejectedCard(
            @Param("cardId") long cardId,
            @Param("cardVersion") String cardVersion,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.ast_card_refund_request
            SET status = 'EXECUTED', execution_idempotency_key = #{key},
                executed_at = sysdatetime(), executed_by = #{operatorId}
            WHERE id = #{id} AND status = 'APPROVED'
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int markCardRefundExecuted(
            @Param("id") long id,
            @Param("version") String version,
            @Param("key") String key,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.ast_card_refund_request
            SET commission_adjustment_status = #{status}
            WHERE id = #{requestId} AND status = 'EXECUTED'
              AND commission_adjustment_status = 'PENDING_MODULE'
            """)
    int updateCardRefundCommissionStatus(
            @Param("requestId") long requestId,
            @Param("status") String status);

    @Insert("""
            INSERT INTO dbo.ast_member_card_ledger (
                ledger_no, member_card_id, service_id, transaction_type,
                before_times, change_times, after_times, value_amount,
                source_type, source_id, occurred_at, correlation_id, note, created_by
            ) VALUES (
                #{ledgerNo}, #{balance.memberCardId}, #{balance.serviceId}, 'REFUND_OUT',
                #{balance.remainingTimes}, -#{balance.remainingTimes}, 0, #{valueAmount},
                'CARD_REFUND', #{requestId}, sysdatetime(),
                CONCAT('card-refund:', #{requestId}, ':balance:', #{balance.id}),
                N'退卡清零剩余次数', #{operatorId}
            )
            """)
    void insertCardRefundOutLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("requestId") long requestId,
            @Param("balance") MemberCardBalanceRow balance,
            @Param("valueAmount") BigDecimal valueAmount,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.ast_member_card
            SET status = 'REFUNDED', updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{cardId} AND status = 'FROZEN'
              AND row_version = CONVERT(binary(8), #{cardVersion}, 1)
            """)
    int markCardRefunded(
            @Param("cardId") long cardId,
            @Param("cardVersion") String cardVersion,
            @Param("operatorId") long operatorId);

    @Insert("""
            INSERT INTO dbo.ast_card_refund_payment (
                refund_no, request_id, payment_method_id, refund_amount, refund_status,
                external_refund_reference, idempotency_key, completed_at, created_by
            ) VALUES (
                #{refundNo}, #{request.id}, #{request.refundMethodId}, #{request.refundAmount}, 'SUCCESS',
                #{externalReference}, #{idempotencyKey}, sysdatetime(), #{operatorId}
            )
            """)
    void insertCardRefundPayment(
            @Param("refundNo") String refundNo,
            @Param("request") CardRefundRequestSummary request,
            @Param("externalReference") String externalReference,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("operatorId") long operatorId);
}
