package com.yuezhijian.server.member;

import java.math.BigDecimal;

public record MemberAssets(
        BigDecimal availableBalance,
        BigDecimal frozenBalance,
        BigDecimal totalRecharged,
        int availablePoints,
        int lifetimePoints,
        int cardCount) {
}
