package com.yuezhijian.server.trade;

import java.util.List;
import java.util.Optional;

public interface TradeRepository {
    List<PaymentMethodOption> paymentMethods(long storeId);

    List<BillSummary> search(BillQuery query);

    Optional<BillDetail> findById(long id);

    Optional<BillSummary> findByAppointmentId(long appointmentId);

    Optional<CreatedBill> findByIdempotencyKey(String idempotencyKey);

    Optional<BillDetail> findBySettlementIdempotencyKey(String idempotencyKey);

    CreatedBill create(BillDraft draft);

    BillDetail addLine(AddBillLineCommand command);

    SettlementQuote createQuote(SettlementQuoteDraft draft);

    Optional<SettlementQuote> findQuote(String quoteNo);

    BillDetail settle(SettleBillCommand command);

    BillDetail voidBill(long billId, String reasonCode, String note, String version, long operatorId);
}
