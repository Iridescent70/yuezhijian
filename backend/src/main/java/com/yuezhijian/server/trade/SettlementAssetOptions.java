package com.yuezhijian.server.trade;

import com.yuezhijian.server.asset.BalanceAccount;
import com.yuezhijian.server.asset.PointAccount;
import com.yuezhijian.server.benefit.VoucherSettlementOption;
import java.util.List;

public record SettlementAssetOptions(
        BalanceAccount balanceAccount,
        PointAccount pointAccount,
        int pointsPerYuan,
        List<CardSettlementOption> cardOptions,
        List<VoucherSettlementOption> voucherOptions) {
    public SettlementAssetOptions {
        cardOptions = List.copyOf(cardOptions);
        voucherOptions = List.copyOf(voucherOptions);
    }
}
