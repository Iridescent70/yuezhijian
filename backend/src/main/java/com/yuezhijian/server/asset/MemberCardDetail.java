package com.yuezhijian.server.asset;

import java.util.List;

public record MemberCardDetail(
        MemberCardSummary card,
        List<MemberCardBalanceItem> balances,
        List<MemberCardLedgerItem> ledgers) {}
