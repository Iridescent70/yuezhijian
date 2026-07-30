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

    BillDetail updateLine(UpdateBillLineCommand command);

    BillDetail removeLine(RemoveBillLineCommand command);

    BillDetail applyDiscount(BillDiscountDraft draft);

    SettlementQuote createQuote(SettlementQuoteDraft draft);

    Optional<SettlementQuote> findQuote(String quoteNo);

    BillDetail settle(SettleBillCommand command);

    BillDetail voidBill(long billId, String reasonCode, String note, String version, long operatorId);

    List<ReversalSummary> reversals(String status);

    Optional<ReversalDetail> findReversal(long id);

    Optional<ReversalDetail> findReversalByRequestKey(String idempotencyKey);

    Optional<ReversalDetail> findReversalByExecutionKey(String idempotencyKey);

    Optional<ReversalDetail> findActiveReversalByBill(long billId);

    ReversalDetail createReversal(ReversalDraft draft);

    ReversalDetail reviewReversal(long id, boolean approved, String comment, String version, long operatorId);

    ReversalDetail executeReversal(ReversalExecutionCommand command);
}
