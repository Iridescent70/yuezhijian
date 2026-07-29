package com.yuezhijian.server.asset;

import java.util.List;
import java.util.Optional;

public interface AssetRepository {
    Optional<BalanceAccount> findBalanceAccount(long memberId);
    List<BalanceLedgerItem> balanceLedgers(long memberId, int limit);
    Optional<PointAccount> findPointAccount(long memberId);
    List<PointLedgerItem> pointLedgers(long memberId, int limit);
    RechargeQuote createRechargeQuote(RechargeQuoteDraft draft);
    Optional<RechargeQuote> findRechargeQuote(String quoteNo);
    Optional<RechargeOrder> findRechargeOrder(long id);
    Optional<RechargeOrder> findRechargeOrderByIdempotencyKey(String idempotencyKey);
    RechargeOrder createRechargeOrder(RechargeOrderDraft draft);
    RechargeOrder confirmRecharge(long id, String version, long operatorId);
    RechargeOrder cancelRecharge(long id, String version, String reason, long operatorId);
    PointAccount adjustPoints(PointAdjustmentCommand command);
    int pointsPerYuan();
    void consumeBalance(BalanceSettlementConsumption command);
    void consumePoints(PointSettlementConsumption command);
}
