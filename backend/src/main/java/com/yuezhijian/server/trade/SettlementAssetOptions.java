package com.yuezhijian.server.trade;

import com.yuezhijian.server.asset.BalanceAccount;
import com.yuezhijian.server.asset.PointAccount;
import java.util.List;

public record SettlementAssetOptions(
        BalanceAccount balanceAccount,
        PointAccount pointAccount,
        int pointsPerYuan,
        List<CardSettlementOption> cardOptions) {
    public SettlementAssetOptions {
        cardOptions = List.copyOf(cardOptions);
    }
}
