package com.yuezhijian.server.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper {
    String GIFT_SELECT = """
            SELECT gift.id, gift.gift_code AS code, gift.gift_name AS name,
                   gift.category_id AS categoryId, category.name AS categoryName,
                   gift.unit_id AS unitId, unit.unit_name AS unitName,
                   unit.decimal_places AS unitDecimalPlaces, gift.point_price AS pointPrice,
                   gift.cost_price AS costPrice, gift.low_stock_threshold AS lowStockThreshold,
                   gift.description, gift.status,
                   CONVERT(varchar(18), CAST(gift.row_version AS varbinary(8)), 1) AS version
            FROM dbo.cat_gift gift
            JOIN dbo.cat_category category ON category.id = gift.category_id
            JOIN dbo.cat_unit unit ON unit.id = gift.unit_id
            """;

    String TRANSFER_HEADER_SELECT = """
            SELECT transfer.id, transfer.transfer_no AS transferNo,
                   transfer.source_store_id AS sourceStoreId, source.store_name AS sourceStoreName,
                   transfer.target_store_id AS targetStoreId, target.store_name AS targetStoreName,
                   transfer.transfer_date AS transferDate, transfer.remarks, transfer.status,
                   transfer.confirmed_at AS confirmedAt, transfer.voided_at AS voidedAt,
                   transfer.reversed_at AS reversedAt, transfer.action_reason AS actionReason,
                   transfer.created_at AS createdAt, transfer.created_by AS createdBy,
                   COALESCE(operator.full_name, operator.username) AS createdByName,
                   transfer.idempotency_key AS idempotencyKey,
                   CONVERT(varchar(18), CAST(transfer.row_version AS varbinary(8)), 1) AS version
            FROM dbo.inv_transfer transfer
            JOIN dbo.org_store source ON source.id = transfer.source_store_id
            JOIN dbo.org_store target ON target.id = transfer.target_store_id
            JOIN dbo.iam_user operator ON operator.id = transfer.created_by
            """;

    String COUNT_HEADER_SELECT = """
            SELECT counting.id, counting.count_no AS countNo, counting.name,
                   counting.store_id AS storeId, store.store_name AS storeName,
                   counting.count_date AS countDate, counting.remarks, counting.status,
                   counting.confirmed_at AS confirmedAt, counting.voided_at AS voidedAt,
                   counting.action_reason AS actionReason, counting.created_at AS createdAt,
                   counting.created_by AS createdBy,
                   COALESCE(operator.full_name, operator.username) AS createdByName,
                   counting.idempotency_key AS idempotencyKey,
                   CONVERT(varchar(18), CAST(counting.row_version AS varbinary(8)), 1) AS version
            FROM dbo.inv_count counting
            JOIN dbo.org_store store ON store.id = counting.store_id
            JOIN dbo.iam_user operator ON operator.id = counting.created_by
            """;

    @Select("""
            <script>
            """ + GIFT_SELECT + """
            WHERE 1 = 1
            <if test="keyword != null">
              AND (gift.gift_code LIKE CONCAT('%', #{keyword}, '%')
                   OR gift.gift_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND gift.status = #{status}</if>
            ORDER BY gift.id DESC
            OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY
            </script>
            """)
    List<Gift> findGifts(
            @Param("keyword") String keyword, @Param("status") String status,
            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT_BIG(1) FROM dbo.cat_gift gift WHERE 1 = 1
            <if test="keyword != null">
              AND (gift.gift_code LIKE CONCAT('%', #{keyword}, '%')
                   OR gift.gift_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND gift.status = #{status}</if>
            </script>
            """)
    long countGifts(@Param("keyword") String keyword, @Param("status") String status);

    @Select(GIFT_SELECT + " WHERE gift.id = #{id}")
    Gift findGift(long id);

    @Select(GIFT_SELECT + " WHERE gift.gift_code = #{code}")
    Gift findGiftByCode(String code);

    @Select(value = """
            INSERT INTO dbo.cat_gift (
                gift_code, gift_name, category_id, unit_id, point_price,
                cost_price, low_stock_threshold, description, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{code}, #{name}, #{categoryId}, #{unitId}, #{pointPrice},
                #{costPrice}, #{lowStockThreshold}, #{description}, #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insertGift(NewGift gift);

    @Update("""
            UPDATE dbo.cat_gift
            SET gift_name = #{name}, category_id = #{categoryId}, unit_id = #{unitId},
                point_price = #{pointPrice}, cost_price = #{costPrice},
                low_stock_threshold = #{lowStockThreshold}, description = #{description},
                status = #{status}, updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updateGift(GiftUpdate update);

    @Select("""
            <script>
            SELECT store.id AS storeId, store.store_name AS storeName,
                   gift.id AS giftId, gift.gift_code AS giftCode, gift.gift_name AS giftName,
                   unit.unit_name AS unitName, unit.decimal_places AS unitDecimalPlaces,
                   COALESCE(stock.on_hand_quantity, 0) AS onHandQuantity,
                   gift.low_stock_threshold AS lowStockThreshold,
                   CAST(CASE WHEN COALESCE(stock.on_hand_quantity, 0) &lt;= gift.low_stock_threshold
                             THEN 1 ELSE 0 END AS bit) AS lowStock,
                   gift.status AS giftStatus,
                   COALESCE(CONVERT(varchar(18), CAST(stock.row_version AS varbinary(8)), 1), '0') AS version
            FROM dbo.org_store store
            CROSS JOIN dbo.cat_gift gift
            JOIN dbo.cat_unit unit ON unit.id = gift.unit_id
            LEFT JOIN dbo.inv_stock stock ON stock.store_id = store.id AND stock.gift_id = gift.id
            WHERE store.id = #{storeId}
            <if test="keyword != null">
              AND (gift.gift_code LIKE CONCAT('%', #{keyword}, '%')
                   OR gift.gift_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="lowStock != null">
              AND CASE WHEN COALESCE(stock.on_hand_quantity, 0) &lt;= gift.low_stock_threshold
                       THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END = #{lowStock}
            </if>
            ORDER BY gift.gift_code, gift.id
            OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY
            </script>
            """)
    List<StockItem> findStocks(
            @Param("storeId") long storeId, @Param("keyword") String keyword,
            @Param("lowStock") Boolean lowStock, @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT_BIG(1)
            FROM dbo.cat_gift gift
            LEFT JOIN dbo.inv_stock stock ON stock.store_id = #{storeId} AND stock.gift_id = gift.id
            WHERE 1 = 1
            <if test="keyword != null">
              AND (gift.gift_code LIKE CONCAT('%', #{keyword}, '%')
                   OR gift.gift_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="lowStock != null">
              AND CASE WHEN COALESCE(stock.on_hand_quantity, 0) &lt;= gift.low_stock_threshold
                       THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END = #{lowStock}
            </if>
            </script>
            """)
    long countStocks(
            @Param("storeId") long storeId, @Param("keyword") String keyword,
            @Param("lowStock") Boolean lowStock);

    @Select("""
            SELECT ledger.id, ledger.ledger_no AS ledgerNo, ledger.store_id AS storeId,
                   store.store_name AS storeName, ledger.gift_id AS giftId,
                   gift.gift_code AS giftCode, gift.gift_name AS giftName,
                   ledger.transaction_type AS transactionType,
                   ledger.before_quantity AS beforeQuantity, ledger.change_quantity AS changeQuantity,
                   ledger.after_quantity AS afterQuantity, ledger.source_type AS sourceType,
                   ledger.source_id AS sourceId, ledger.source_line_id AS sourceLineId,
                   ledger.occurred_at AS occurredAt, ledger.reversed_ledger_id AS reversedLedgerId,
                   ledger.note, COALESCE(operator.full_name, operator.username) AS operatorName
            FROM dbo.inv_stock_ledger ledger
            JOIN dbo.org_store store ON store.id = ledger.store_id
            JOIN dbo.cat_gift gift ON gift.id = ledger.gift_id
            JOIN dbo.iam_user operator ON operator.id = ledger.created_by
            WHERE ledger.store_id = #{storeId} AND ledger.gift_id = #{giftId}
            ORDER BY ledger.occurred_at DESC, ledger.id DESC
            OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY
            """)
    List<StockLedgerItem> findStockLedgers(
            @Param("storeId") long storeId, @Param("giftId") long giftId,
            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            SELECT COUNT_BIG(1) FROM dbo.inv_stock_ledger
            WHERE store_id = #{storeId} AND gift_id = #{giftId}
            """)
    long countStockLedgers(@Param("storeId") long storeId, @Param("giftId") long giftId);

    @Insert("""
            INSERT INTO dbo.inv_stock (store_id, gift_id, created_by, updated_by)
            SELECT #{storeId}, #{giftId}, #{operatorId}, #{operatorId}
            WHERE NOT EXISTS (
                SELECT 1 FROM dbo.inv_stock WITH (UPDLOCK, HOLDLOCK)
                WHERE store_id = #{storeId} AND gift_id = #{giftId}
            )
            """)
    int ensureStock(
            @Param("storeId") long storeId, @Param("giftId") long giftId,
            @Param("operatorId") long operatorId);

    @Select("""
            SELECT id, store_id AS storeId, gift_id AS giftId,
                   on_hand_quantity AS onHandQuantity, row_version AS rowVersion
            FROM dbo.inv_stock WITH (UPDLOCK, HOLDLOCK)
            WHERE store_id = #{storeId} AND gift_id = #{giftId}
            """)
    StockLockRow lockStock(@Param("storeId") long storeId, @Param("giftId") long giftId);

    @Update("""
            UPDATE dbo.inv_stock
            SET on_hand_quantity = #{afterQuantity}, updated_at = #{occurredAt}, updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = #{rowVersion} AND #{afterQuantity} >= 0
            """)
    int updateStock(
            @Param("id") long id, @Param("afterQuantity") BigDecimal afterQuantity,
            @Param("occurredAt") LocalDateTime occurredAt, @Param("operatorId") long operatorId,
            @Param("rowVersion") byte[] rowVersion);

    @Select(value = """
            INSERT INTO dbo.inv_stock_ledger (
                ledger_no, store_id, gift_id, transaction_type,
                before_quantity, change_quantity, after_quantity,
                source_type, source_id, source_line_id, occurred_at,
                reversed_ledger_id, note, created_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{ledgerNo}, #{storeId}, #{giftId}, #{transactionType},
                #{beforeQuantity}, #{changeQuantity}, #{afterQuantity},
                #{sourceType}, #{sourceId}, #{sourceLineId}, #{occurredAt},
                #{reversedLedgerId}, #{note}, #{operatorId}
            )
            """, affectData = true)
    long insertStockLedger(
            @Param("ledgerNo") String ledgerNo,
            @Param("storeId") long storeId,
            @Param("giftId") long giftId,
            @Param("transactionType") String transactionType,
            @Param("beforeQuantity") BigDecimal beforeQuantity,
            @Param("changeQuantity") BigDecimal changeQuantity,
            @Param("afterQuantity") BigDecimal afterQuantity,
            @Param("sourceType") String sourceType,
            @Param("sourceId") long sourceId,
            @Param("sourceLineId") Long sourceLineId,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("reversedLedgerId") Long reversedLedgerId,
            @Param("note") String note,
            @Param("operatorId") long operatorId);

    @Select("""
            <script>
            SELECT transfer.id, transfer.transfer_no AS transferNo,
                   transfer.source_store_id AS sourceStoreId, source.store_name AS sourceStoreName,
                   transfer.target_store_id AS targetStoreId, target.store_name AS targetStoreName,
                   transfer.transfer_date AS transferDate,
                   COUNT(line.id) AS lineCount, COALESCE(SUM(line.quantity), 0) AS totalQuantity,
                   transfer.status, transfer.created_at AS createdAt,
                   COALESCE(operator.full_name, operator.username) AS createdByName,
                   CONVERT(varchar(18), CAST(transfer.row_version AS varbinary(8)), 1) AS version
            FROM dbo.inv_transfer transfer
            JOIN dbo.org_store source ON source.id = transfer.source_store_id
            JOIN dbo.org_store target ON target.id = transfer.target_store_id
            JOIN dbo.iam_user operator ON operator.id = transfer.created_by
            JOIN dbo.inv_transfer_line line ON line.transfer_id = transfer.id
            WHERE 1 = 1
            <if test="storeId != null">
              AND (transfer.source_store_id = #{storeId} OR transfer.target_store_id = #{storeId})
            </if>
            <if test="keyword != null">AND transfer.transfer_no LIKE CONCAT('%', #{keyword}, '%')</if>
            <if test="status != null">AND transfer.status = #{status}</if>
            GROUP BY transfer.id, transfer.transfer_no, transfer.source_store_id, source.store_name,
                     transfer.target_store_id, target.store_name, transfer.transfer_date, transfer.status,
                     transfer.created_at, operator.full_name, operator.username, transfer.row_version
            ORDER BY transfer.created_at DESC, transfer.id DESC
            OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY
            </script>
            """)
    List<TransferSummary> findTransfers(
            @Param("storeId") Long storeId, @Param("keyword") String keyword, @Param("status") String status,
            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT_BIG(1) FROM dbo.inv_transfer transfer WHERE 1 = 1
            <if test="storeId != null">
              AND (transfer.source_store_id = #{storeId} OR transfer.target_store_id = #{storeId})
            </if>
            <if test="keyword != null">AND transfer.transfer_no LIKE CONCAT('%', #{keyword}, '%')</if>
            <if test="status != null">AND transfer.status = #{status}</if>
            </script>
            """)
    long countTransfers(
            @Param("storeId") Long storeId, @Param("keyword") String keyword, @Param("status") String status);

    @Select(TRANSFER_HEADER_SELECT + " WHERE transfer.id = #{id}")
    TransferHeaderRow findTransfer(long id);

    @Select(TRANSFER_HEADER_SELECT + " WHERE transfer.idempotency_key = #{key}")
    TransferHeaderRow findTransferByIdempotencyKey(String key);

    @Select("""
            SELECT line.id, line.gift_id AS giftId, gift.gift_code AS giftCode,
                   gift.gift_name AS giftName, unit.unit_name AS unitName,
                   unit.decimal_places AS unitDecimalPlaces, line.quantity, line.note,
                   line.source_ledger_id AS sourceLedgerId, line.target_ledger_id AS targetLedgerId
            FROM dbo.inv_transfer_line line
            JOIN dbo.cat_gift gift ON gift.id = line.gift_id
            JOIN dbo.cat_unit unit ON unit.id = gift.unit_id
            WHERE line.transfer_id = #{transferId}
            ORDER BY line.id
            """)
    List<TransferLine> findTransferLines(long transferId);

    @Select(value = """
            INSERT INTO dbo.inv_transfer (
                transfer_no, source_store_id, target_store_id, transfer_date,
                remarks, idempotency_key, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{transferNo}, #{sourceStoreId}, #{targetStoreId}, #{transferDate},
                #{remarks}, #{idempotencyKey}, #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insertTransfer(NewTransfer transfer);

    @Select(value = """
            INSERT INTO dbo.inv_transfer_line (transfer_id, gift_id, quantity, note)
            OUTPUT INSERTED.id
            VALUES (#{transferId}, #{line.giftId}, #{line.quantity}, #{line.note})
            """, affectData = true)
    long insertTransferLine(@Param("transferId") long transferId, @Param("line") TransferLineRequest line);

    @Update("""
            UPDATE dbo.inv_transfer_line
            SET source_ledger_id = #{sourceLedgerId}, target_ledger_id = #{targetLedgerId}
            WHERE id = #{lineId} AND source_ledger_id IS NULL AND target_ledger_id IS NULL
            """)
    int linkTransferLedgers(
            @Param("lineId") long lineId, @Param("sourceLedgerId") long sourceLedgerId,
            @Param("targetLedgerId") long targetLedgerId);

    @Update("""
            UPDATE dbo.inv_transfer
            SET status = #{targetStatus}, action_reason = #{reason},
                confirmed_at = CASE WHEN #{targetStatus} = 'CONFIRMED' THEN #{occurredAt} ELSE confirmed_at END,
                voided_at = CASE WHEN #{targetStatus} = 'VOIDED' THEN #{occurredAt} ELSE voided_at END,
                reversed_at = CASE WHEN #{targetStatus} = 'REVERSED' THEN #{occurredAt} ELSE reversed_at END,
                updated_at = #{occurredAt}, updated_by = #{operatorId}
            WHERE id = #{id} AND status = #{expectedStatus}
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updateTransferStatus(
            @Param("id") long id, @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus, @Param("reason") String reason,
            @Param("occurredAt") LocalDateTime occurredAt, @Param("operatorId") long operatorId,
            @Param("version") String version);

    @Select("""
            <script>
            SELECT counting.id, counting.count_no AS countNo, counting.name,
                   counting.store_id AS storeId, store.store_name AS storeName,
                   counting.count_date AS countDate, COUNT(line.id) AS lineCount,
                   SUM(CASE WHEN line.actual_quantity IS NOT NULL
                                  AND line.actual_quantity &lt;&gt; line.book_quantity THEN 1 ELSE 0 END)
                       AS differenceLineCount,
                   COALESCE(SUM(COALESCE(line.actual_quantity, line.book_quantity) - line.book_quantity), 0)
                       AS differenceQuantity,
                   counting.status, counting.created_at AS createdAt,
                   COALESCE(operator.full_name, operator.username) AS createdByName,
                   CONVERT(varchar(18), CAST(counting.row_version AS varbinary(8)), 1) AS version
            FROM dbo.inv_count counting
            JOIN dbo.org_store store ON store.id = counting.store_id
            JOIN dbo.iam_user operator ON operator.id = counting.created_by
            JOIN dbo.inv_count_line line ON line.count_id = counting.id
            WHERE 1 = 1
            <if test="storeId != null">AND counting.store_id = #{storeId}</if>
            <if test="keyword != null">
              AND (counting.count_no LIKE CONCAT('%', #{keyword}, '%')
                   OR counting.name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND counting.status = #{status}</if>
            GROUP BY counting.id, counting.count_no, counting.name, counting.store_id,
                     store.store_name, counting.count_date, counting.status, counting.created_at,
                     operator.full_name, operator.username, counting.row_version
            ORDER BY counting.created_at DESC, counting.id DESC
            OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY
            </script>
            """)
    List<CountSummary> findCounts(
            @Param("storeId") Long storeId, @Param("keyword") String keyword, @Param("status") String status,
            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT_BIG(1) FROM dbo.inv_count counting WHERE 1 = 1
            <if test="storeId != null">AND counting.store_id = #{storeId}</if>
            <if test="keyword != null">
              AND (counting.count_no LIKE CONCAT('%', #{keyword}, '%')
                   OR counting.name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND counting.status = #{status}</if>
            </script>
            """)
    long countCounts(
            @Param("storeId") Long storeId, @Param("keyword") String keyword, @Param("status") String status);

    @Select(COUNT_HEADER_SELECT + " WHERE counting.id = #{id}")
    CountHeaderRow findCount(long id);

    @Select(COUNT_HEADER_SELECT + " WHERE counting.idempotency_key = #{key}")
    CountHeaderRow findCountByIdempotencyKey(String key);

    @Select("""
            SELECT line.id, line.gift_id AS giftId, gift.gift_code AS giftCode,
                   gift.gift_name AS giftName, unit.unit_name AS unitName,
                   unit.decimal_places AS unitDecimalPlaces, line.book_quantity AS bookQuantity,
                   line.actual_quantity AS actualQuantity,
                   CASE WHEN line.actual_quantity IS NULL THEN NULL
                        ELSE line.actual_quantity - line.book_quantity END AS differenceQuantity,
                   line.stock_ledger_id AS stockLedgerId
            FROM dbo.inv_count_line line
            JOIN dbo.cat_gift gift ON gift.id = line.gift_id
            JOIN dbo.cat_unit unit ON unit.id = gift.unit_id
            WHERE line.count_id = #{countId}
            ORDER BY line.id
            """)
    List<CountLine> findCountLines(long countId);

    @Select(value = """
            INSERT INTO dbo.inv_count (
                count_no, name, store_id, count_date, remarks,
                idempotency_key, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{countNo}, #{name}, #{storeId}, #{countDate}, #{remarks},
                #{idempotencyKey}, #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insertCount(NewCount count);

    @Select(value = """
            INSERT INTO dbo.inv_count_line (count_id, gift_id, book_quantity)
            OUTPUT INSERTED.id
            VALUES (#{countId}, #{giftId}, #{bookQuantity})
            """, affectData = true)
    long insertCountLine(
            @Param("countId") long countId, @Param("giftId") long giftId,
            @Param("bookQuantity") BigDecimal bookQuantity);

    @Update("""
            UPDATE dbo.inv_count_line SET actual_quantity = #{actualQuantity}
            WHERE id = #{lineId} AND count_id = #{countId} AND stock_ledger_id IS NULL
            """)
    int updateCountLine(
            @Param("countId") long countId, @Param("lineId") long lineId,
            @Param("actualQuantity") BigDecimal actualQuantity);

    @Update("""
            UPDATE dbo.inv_count_line SET stock_ledger_id = #{ledgerId}
            WHERE id = #{lineId} AND stock_ledger_id IS NULL
            """)
    int linkCountLedger(@Param("lineId") long lineId, @Param("ledgerId") long ledgerId);

    @Update("""
            <script>
            UPDATE dbo.inv_count
            SET status = #{targetStatus}, action_reason = #{reason},
                confirmed_at = CASE WHEN #{targetStatus} = 'CONFIRMED' THEN #{occurredAt} ELSE confirmed_at END,
                voided_at = CASE WHEN #{targetStatus} = 'VOIDED' THEN #{occurredAt} ELSE voided_at END,
                updated_at = #{occurredAt}, updated_by = #{operatorId}
            WHERE id = #{id} AND status IN
              <foreach collection="expectedStatuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND row_version = CONVERT(binary(8), #{version}, 1)
            </script>
            """)
    int updateCountStatus(
            @Param("id") long id, @Param("expectedStatuses") List<String> expectedStatuses,
            @Param("targetStatus") String targetStatus, @Param("reason") String reason,
            @Param("occurredAt") LocalDateTime occurredAt, @Param("operatorId") long operatorId,
            @Param("version") String version);
}
