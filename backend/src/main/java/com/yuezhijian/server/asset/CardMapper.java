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
}
