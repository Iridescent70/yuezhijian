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
            SELECT method.id, method.method_code AS code, method.method_name AS name,
                   method.method_type AS type, method.is_electronic AS electronic,
                   method.included_in_revenue AS includedInRevenue,
                   method.needs_external_ref AS needsExternalReference, config.sort_no AS sortNo
            FROM dbo.cat_payment_method_store config
            JOIN dbo.cat_payment_method method ON method.id = config.payment_method_id
            WHERE config.store_id = #{storeId} AND config.enabled = 1 AND method.status = 'ACTIVE'
            ORDER BY config.sort_no, method.id
            """)
    List<PaymentMethodOption> findPaymentMethods(long storeId);

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

    @Select(SUMMARY_SELECT + " WHERE EXISTS (SELECT 1 FROM dbo.trd_payment payment WHERE payment.bill_id = bill.id AND payment.idempotency_key = CONCAT(#{key}, ':0'))")
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
            WHERE line.bill_id = #{billId}
            ORDER BY line.line_no, line.id
            """)
    List<BillLine> findLines(long billId);

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
            SET original_amount = #{total}, receivable_amount = #{total}, status = 'PENDING_PAYMENT',
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{billId} AND row_version = CONVERT(binary(8), #{version}, 1)
              AND status IN ('DRAFT', 'PENDING_PAYMENT')
            """)
    int updateTotals(
            @Param("billId") long billId,
            @Param("total") BigDecimal total,
            @Param("version") String version,
            @Param("operatorId") long operatorId);

    @Select(value = """
            INSERT INTO dbo.trd_settlement_quote (
                quote_no, bill_id, bill_row_version, receivable_amount, payment_total,
                change_amount, difference_amount, request_json, expires_at, created_by
            )
            OUTPUT INSERTED.id
            SELECT #{quoteNo}, bill.id, bill.row_version, #{receivableAmount}, #{paymentTotal},
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

    @Select("""
            SELECT quote.id, quote.quote_no AS quoteNo, quote.bill_id AS billId,
                   CONVERT(varchar(18), quote.bill_row_version, 1) AS billVersion,
                   quote.receivable_amount AS receivableAmount, quote.payment_total AS paymentTotal,
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
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{billId} AND row_version = CONVERT(binary(8), #{billVersion}, 1)
              AND status IN ('DRAFT', 'PENDING_PAYMENT')
            """)
    int settleBill(
            @Param("billId") long billId,
            @Param("billVersion") String billVersion,
            @Param("changeAmount") BigDecimal changeAmount,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.trd_bill_line
            SET actual_amount = receivable_amount, commission_base = receivable_amount
            WHERE bill_id = #{billId}
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
