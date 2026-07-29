package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TradeMapper {
    String SUMMARY_SELECT = """
            SELECT bill.id, bill.bill_no AS billNo, bill.appointment_id AS appointmentId,
                   bill.member_id AS memberId,
                   COALESCE(member.full_name, bill.guest_name, appointment.guest_name) AS customerName,
                   CASE WHEN COALESCE(member.mobile_last4, bill.mobile_last4, appointment.mobile_last4) IS NULL THEN NULL
                        ELSE CONCAT('*******', RTRIM(COALESCE(member.mobile_last4, bill.mobile_last4, appointment.mobile_last4))) END AS maskedMobile,
                   bill.store_id AS storeId, store.store_name AS storeName, bill.source_type AS sourceType,
                   bill.person_count AS personCount, bill.original_amount AS originalAmount,
                   bill.discount_amount AS discountAmount, bill.receivable_amount AS receivableAmount,
                   bill.received_amount AS receivedAmount, bill.change_amount AS changeAmount,
                   bill.status, bill.note, bill.settled_at AS settledAt, bill.created_at AS createdAt,
                   CONVERT(varchar(18), bill.row_version, 1) AS version
            FROM dbo.trd_bill bill
            LEFT JOIN dbo.mem_member member ON member.id = bill.member_id
            LEFT JOIN dbo.apt_appointment appointment ON appointment.id = bill.appointment_id
            JOIN dbo.org_store store ON store.id = bill.store_id
            """;

    @Select("""
            <script>
            """ + SUMMARY_SELECT + """
            WHERE bill.store_id = #{query.storeId}
              AND bill.created_at &gt;= #{from} AND bill.created_at &lt; #{until}
            <if test="query.status != null">
              AND bill.status = #{query.status}
            </if>
            <if test="query.keyword != null">
              AND (bill.bill_no LIKE CONCAT('%', #{query.keyword}, '%')
                   OR member.full_name LIKE CONCAT('%', #{query.keyword}, '%')
                   OR bill.guest_name LIKE CONCAT('%', #{query.keyword}, '%')
                   OR appointment.guest_name LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            ORDER BY bill.created_at DESC, bill.id DESC
            </script>
            """)
    List<BillSummary> search(
            @Param("query") BillQuery query,
            @Param("from") LocalDateTime from,
            @Param("until") LocalDateTime until);

    @Select(SUMMARY_SELECT + " WHERE bill.id = #{id}")
    BillSummary findSummaryById(long id);

    @Select(SUMMARY_SELECT + " WHERE bill.appointment_id = #{appointmentId}")
    BillSummary findByAppointmentId(long appointmentId);

    @Select(SUMMARY_SELECT + " WHERE bill.idempotency_key = #{idempotencyKey}")
    BillSummary findByIdempotencyKey(String idempotencyKey);

    @Select(SUMMARY_SELECT + " WHERE bill.settlement_idempotency_key = #{key}")
    BillSummary findBySettlementIdempotency(String key);

    @Select("""
            SELECT line.id, line.line_no AS lineNo, line.item_type AS itemType, line.item_id AS itemId,
                   line.item_code_snapshot AS itemCode, line.item_name_snapshot AS itemName,
                   line.unit_price AS unitPrice, line.quantity, line.original_amount AS originalAmount,
                   line.discount_amount AS discountAmount, line.receivable_amount AS receivableAmount,
                   line.actual_amount AS actualAmount, allocation.employee_id AS employeeId,
                   allocation.employee_name AS employeeName, line.note
            FROM dbo.trd_bill_line line
            OUTER APPLY (
                SELECT TOP 1 relation.employee_id, employee.name AS employee_name
                FROM dbo.trd_bill_line_employee relation
                JOIN dbo.org_employee employee ON employee.id = relation.employee_id
                WHERE relation.bill_line_id = line.id AND relation.employee_role = 'SERVICE'
                ORDER BY relation.id
            ) allocation
            WHERE line.bill_id = #{billId} AND line.line_status = 'ACTIVE'
            ORDER BY line.line_no, line.id
            """)
    List<BillLine> findLines(long billId);

    @Select("""
            SELECT id, batch_no AS batchNo, bill_line_id AS billLineId, discount_type AS discountType,
                   original_amount AS originalAmount, discount_amount AS discountAmount, reason,
                   authorization_user_id AS authorizationUserId, created_at AS createdAt
            FROM dbo.trd_bill_discount
            WHERE bill_id = #{billId} AND active = 1
            ORDER BY id
            """)
    List<BillDiscountItem> findDiscounts(long billId);

    @Select("""
            SELECT id, asset_type AS assetType, member_id AS memberId, voucher_code_id AS voucherCodeId,
                   member_card_id AS memberCardId,
                   member_card_balance_id AS memberCardBalanceId, bill_line_id AS billLineId,
                   service_id AS serviceId, quantity, amount, asset_ledger_id AS assetLedgerId,
                   display_name AS displayName,
                   created_at AS createdAt
            FROM dbo.trd_bill_asset_usage
            WHERE bill_id = #{billId}
            ORDER BY created_at, id
            """)
    List<BillAssetUsageItem> findAssetUsages(long billId);

    @Select("""
            SELECT payment.id, payment.payment_no AS paymentNo,
                   payment.payment_method_id AS paymentMethodId, method.method_name AS paymentMethodName,
                   payment.amount, payment.payment_status AS status,
                   payment.external_order_no AS externalReference, payment.paid_at AS paidAt
            FROM dbo.trd_payment payment
            JOIN dbo.cat_payment_method method ON method.id = payment.payment_method_id
            WHERE payment.bill_id = #{billId}
            ORDER BY payment.id
            """)
    List<BillPayment> findPayments(long billId);

    @Select("""
            SELECT id, from_status AS fromStatus, to_status AS toStatus,
                   reason_code AS reasonCode, note, occurred_at AS occurredAt, operator_id AS operatorId
            FROM dbo.trd_bill_status_history
            WHERE bill_id = #{billId}
            ORDER BY occurred_at, id
            """)
    List<BillHistoryItem> findHistory(long billId);

    @Select(value = """
            INSERT INTO dbo.trd_bill (
                bill_no, appointment_id, member_id, guest_name, mobile_ciphertext, mobile_hash, mobile_last4,
                store_id, source_type, person_count, original_amount, receivable_amount,
                status, note, idempotency_key, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{billNo}, #{appointmentId}, #{memberId}, #{guestName}, #{mobileCiphertext}, #{mobileHash}, #{mobileLast4},
                #{storeId}, #{sourceType}, #{personCount}, #{originalAmount}, #{receivableAmount},
                #{status}, #{note}, #{idempotencyKey}, #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insertBill(ProtectedBillRow bill);

    @Select(value = """
            INSERT INTO dbo.trd_bill_line (
                bill_id, line_no, item_type, item_id, item_code_snapshot, item_name_snapshot,
                unit_price, quantity, original_amount, receivable_amount, note
            )
            OUTPUT INSERTED.id
            VALUES (
                #{billId}, #{lineNo}, #{line.itemType}, #{line.itemId}, #{line.itemCode}, #{line.itemName},
                #{line.unitPrice}, #{line.quantity}, #{line.amount}, #{line.amount}, #{line.note}
            )
            """, affectData = true)
    long insertLine(
            @Param("billId") long billId,
            @Param("lineNo") int lineNo,
            @Param("line") BillLineDraft line);

    @Select("SELECT COALESCE(MAX(line_no), 0) + 1 FROM dbo.trd_bill_line WHERE bill_id = #{billId}")
    int nextLineNo(long billId);

    @Insert("""
            INSERT INTO dbo.trd_bill_line_employee (
                bill_line_id, employee_id, employee_role, performance_store_id,
                allocation_rate, performance_amount
            ) VALUES (#{lineId}, #{employeeId}, 'SERVICE', #{storeId}, 1, #{amount})
            """)
    void insertLineEmployee(
            @Param("lineId") long lineId,
            @Param("employeeId") long employeeId,
            @Param("storeId") long storeId,
            @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE dbo.trd_bill
            SET original_amount = #{total}, discount_amount = 0, receivable_amount = #{total},
                status = CASE WHEN #{total} = 0 THEN 'DRAFT' ELSE 'PENDING_PAYMENT' END,
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{billId} AND row_version = CONVERT(binary(8), #{version}, 1)
              AND status IN ('DRAFT', 'PENDING_PAYMENT')
            """)
    int updateTotals(
            @Param("billId") long billId,
            @Param("total") BigDecimal total,
            @Param("version") String version,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.trd_bill_line
            SET discount_amount = 0, receivable_amount = original_amount, commission_base = original_amount
            WHERE bill_id = #{billId} AND line_status = 'ACTIVE'
            """)
    void resetLineDiscounts(long billId);

    @Update("""
            UPDATE allocation
            SET performance_amount = line.original_amount
            FROM dbo.trd_bill_line_employee allocation
            JOIN dbo.trd_bill_line line ON line.id = allocation.bill_line_id
            WHERE line.bill_id = #{billId} AND line.line_status = 'ACTIVE'
            """)
    void resetEmployeePerformance(long billId);

    @Update("""
            UPDATE dbo.trd_bill_line
            SET unit_price = #{line.unitPrice}, quantity = #{line.quantity},
                original_amount = #{line.amount}, discount_amount = 0,
                receivable_amount = #{line.amount}, commission_base = #{line.amount}, note = #{line.note}
            WHERE id = #{lineId} AND bill_id = #{billId} AND line_status = 'ACTIVE'
            """)
    int updateLine(
            @Param("billId") long billId,
            @Param("lineId") long lineId,
            @Param("line") BillLineDraft line);

    @Update("DELETE FROM dbo.trd_bill_line_employee WHERE bill_line_id = #{lineId}")
    void deleteLineEmployees(long lineId);

    @Update("""
            UPDATE dbo.trd_bill_line
            SET line_status = 'REMOVED', removed_at = sysdatetime(), removed_by = #{operatorId},
                discount_amount = 0, receivable_amount = original_amount, commission_base = 0
            WHERE id = #{lineId} AND bill_id = #{billId} AND line_status = 'ACTIVE'
            """)
    int removeLine(
            @Param("billId") long billId,
            @Param("lineId") long lineId,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.trd_bill_discount
            SET active = 0, superseded_at = sysdatetime()
            WHERE bill_id = #{billId} AND active = 1
            """)
    void deactivateDiscounts(long billId);

    @Update("""
            UPDATE dbo.trd_bill
            SET original_amount = #{draft.originalAmount}, discount_amount = #{draft.discountAmount},
                receivable_amount = #{receivableAmount},
                status = CASE WHEN #{receivableAmount} = 0 THEN 'DRAFT' ELSE 'PENDING_PAYMENT' END,
                updated_at = sysdatetime(), updated_by = #{draft.operatorId}
            WHERE id = #{draft.billId} AND row_version = CONVERT(binary(8), #{draft.version}, 1)
              AND status IN ('DRAFT', 'PENDING_PAYMENT')
            """)
    int updateDiscountTotals(
            @Param("draft") BillDiscountDraft draft,
            @Param("receivableAmount") BigDecimal receivableAmount);

    @Insert("""
            INSERT INTO dbo.trd_bill_discount (
                batch_no, bill_id, bill_line_id, discount_type, discount_value,
                original_amount, discount_amount, reason, authorization_user_id, created_by
            ) VALUES (
                #{draft.batchNo}, #{draft.billId}, #{allocation.billLineId}, #{draft.discountType},
                #{draft.discountValue}, #{allocation.originalAmount}, #{allocation.discountAmount},
                #{draft.reason}, #{draft.operatorId}, #{draft.operatorId}
            )
            """)
    void insertDiscount(
            @Param("draft") BillDiscountDraft draft,
            @Param("allocation") BillDiscountAllocation allocation);

    @Update("""
            UPDATE dbo.trd_bill_line
            SET discount_amount = #{allocation.discountAmount},
                receivable_amount = #{allocation.receivableAmount},
                commission_base = #{allocation.receivableAmount}
            WHERE id = #{allocation.billLineId} AND bill_id = #{billId} AND line_status = 'ACTIVE'
            """)
    int applyLineDiscount(
            @Param("billId") long billId,
            @Param("allocation") BillDiscountAllocation allocation);

    @Update("""
            UPDATE dbo.trd_bill_line_employee
            SET performance_amount = #{amount}
            WHERE bill_line_id = #{lineId}
            """)
    void updateLinePerformance(
            @Param("lineId") long lineId,
            @Param("amount") BigDecimal amount);

    @Select(value = """
            INSERT INTO dbo.trd_settlement_quote (
                quote_no, bill_id, bill_row_version, receivable_amount, payment_total,
                asset_amount, external_payment_amount,
                change_amount, difference_amount, request_json, expires_at, created_by
            )
            OUTPUT INSERTED.id
            SELECT #{quoteNo}, bill.id, bill.row_version, #{receivableAmount}, #{paymentTotal},
                   #{assetAmount}, #{externalPaymentAmount},
                   #{changeAmount}, #{differenceAmount}, N'{}', #{expiresAt}, #{operatorId}
            FROM dbo.trd_bill bill
            WHERE bill.id = #{billId} AND bill.row_version = CONVERT(binary(8), #{billVersion}, 1)
            """, affectData = true)
    long insertQuote(SettlementQuoteDraft quote);

    @Insert("""
            INSERT INTO dbo.trd_settlement_quote_payment (
                quote_id, payment_method_id, payment_method_code, payment_method_name,
                amount, external_reference, sort_no
            ) VALUES (
                #{quoteId}, #{payment.paymentMethodId}, #{payment.paymentMethodCode},
                #{payment.paymentMethodName}, #{payment.amount}, #{payment.externalReference}, #{sortNo}
            )
            """)
    void insertQuotePayment(
            @Param("quoteId") long quoteId,
            @Param("payment") QuotePayment payment,
            @Param("sortNo") int sortNo);

    @Insert("""
            INSERT INTO dbo.trd_settlement_quote_asset (
                quote_id, asset_type, member_id, voucher_code_id, member_card_id, member_card_balance_id,
                bill_line_id, service_id, quantity, amount, asset_version, display_name, sort_no
            ) VALUES (
                #{quoteId}, #{asset.assetType}, #{asset.memberId}, #{asset.voucherCodeId}, #{asset.memberCardId},
                #{asset.memberCardBalanceId}, #{asset.billLineId}, #{asset.serviceId},
                #{asset.quantity}, #{asset.amount}, #{asset.assetVersion}, #{asset.displayName}, #{sortNo}
            )
            """)
    void insertQuoteAsset(
            @Param("quoteId") long quoteId,
            @Param("asset") SettlementAssetUsage asset,
            @Param("sortNo") int sortNo);

    @Select("""
            SELECT quote.id, quote.quote_no AS quoteNo, quote.bill_id AS billId,
                   CONVERT(varchar(18), quote.bill_row_version, 1) AS billVersion,
                   quote.receivable_amount AS receivableAmount, quote.payment_total AS paymentTotal,
                   quote.asset_amount AS assetAmount,
                   quote.external_payment_amount AS externalPaymentAmount,
                   quote.change_amount AS changeAmount, quote.difference_amount AS differenceAmount,
                   quote.expires_at AS expiresAt, CAST(CASE WHEN quote.used_at IS NULL THEN 0 ELSE 1 END AS bit) AS used
            FROM dbo.trd_settlement_quote quote
            WHERE quote.quote_no = #{quoteNo}
            """)
    QuoteRow findQuote(String quoteNo);

    @Select("""
            SELECT payment_method_id AS paymentMethodId, payment_method_code AS paymentMethodCode,
                   payment_method_name AS paymentMethodName, amount, external_reference AS externalReference
            FROM dbo.trd_settlement_quote_payment
            WHERE quote_id = #{quoteId}
            ORDER BY sort_no, id
            """)
    List<QuotePayment> findQuotePayments(long quoteId);

    @Select("""
            SELECT asset_type AS assetType, member_id AS memberId, voucher_code_id AS voucherCodeId,
                   member_card_id AS memberCardId, member_card_balance_id AS memberCardBalanceId,
                   bill_line_id AS billLineId, service_id AS serviceId,
                   quantity, amount, asset_version AS assetVersion, display_name AS displayName
            FROM dbo.trd_settlement_quote_asset
            WHERE quote_id = #{quoteId}
            ORDER BY sort_no, id
            """)
    List<SettlementAssetUsage> findQuoteAssets(long quoteId);

    @Update("""
            UPDATE dbo.trd_settlement_quote
            SET used_at = sysdatetime()
            WHERE quote_no = #{quoteNo} AND used_at IS NULL AND expires_at >= sysdatetime()
            """)
    int markQuoteUsed(String quoteNo);

    @Update("""
            UPDATE dbo.trd_bill
            SET status = 'SETTLED', received_amount = receivable_amount, change_amount = #{changeAmount},
                settled_at = sysdatetime(), cashier_id = #{operatorId},
                settlement_idempotency_key = #{idempotencyKey},
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{billId} AND row_version = CONVERT(binary(8), #{billVersion}, 1)
              AND status IN ('DRAFT', 'PENDING_PAYMENT')
            """)
    int settleBill(
            @Param("billId") long billId,
            @Param("billVersion") String billVersion,
            @Param("changeAmount") BigDecimal changeAmount,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.trd_bill_line
            SET actual_amount = receivable_amount, commission_base = receivable_amount
            WHERE bill_id = #{billId} AND line_status = 'ACTIVE'
            """)
    void markLinesSettled(long billId);

    @Insert("""
            INSERT INTO dbo.trd_payment (
                payment_no, bill_id, payment_method_id, amount, external_order_no,
                idempotency_key, created_by
            ) VALUES (
                #{paymentNo}, #{billId}, #{payment.paymentMethodId}, #{payment.amount},
                #{payment.externalReference}, #{idempotencyKey}, #{operatorId}
            )
            """)
    void insertPayment(
            @Param("paymentNo") String paymentNo,
            @Param("billId") long billId,
            @Param("payment") QuotePayment payment,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.trd_bill
            SET status = 'VOIDED', voided_at = sysdatetime(), voided_by = #{operatorId},
                void_reason_code = #{reasonCode}, updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{billId} AND row_version = CONVERT(binary(8), #{version}, 1)
              AND status IN ('DRAFT', 'PENDING_PAYMENT')
            """)
    int voidBill(
            @Param("billId") long billId,
            @Param("reasonCode") String reasonCode,
            @Param("version") String version,
            @Param("operatorId") long operatorId);

    @Insert("""
            INSERT INTO dbo.trd_bill_status_history (
                bill_id, from_status, to_status, reason_code, note, operator_id
            ) VALUES (#{billId}, #{fromStatus}, #{toStatus}, #{reasonCode}, #{note}, #{operatorId})
            """)
    void insertHistory(
            @Param("billId") long billId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("reasonCode") String reasonCode,
            @Param("note") String note,
            @Param("operatorId") long operatorId);
}
