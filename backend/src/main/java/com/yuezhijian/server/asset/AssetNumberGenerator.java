package com.yuezhijian.server.asset;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class AssetNumberGenerator {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicLong sequence = new AtomicLong(System.nanoTime() % 100_000);

    public String rechargeQuoteNo() { return next("RQ"); }
    public String rechargeNo() { return next("RC"); }
    public String balanceLedgerNo() { return next("BL"); }
    public String pointLedgerNo() { return next("PL"); }
    public String cardSaleNo() { return next("CS"); }
    public String memberCardNo() { return next("MC"); }
    public String cardLedgerNo() { return next("CL"); }
    public String cardExchangeQuoteNo() { return next("EQ"); }
    public String cardExchangeNo() { return next("EX"); }
    public String cardTransferNo() { return next("CT"); }
    public String cardRefundQuoteNo() { return next("FQ"); }
    public String cardRefundRequestNo() { return next("CR"); }
    public String cardRefundPaymentNo() { return next("CP"); }

    private String next(String prefix) {
        return prefix + LocalDateTime.now().format(FORMAT)
                + String.format("%05d", Math.floorMod(sequence.incrementAndGet(), 100_000));
    }
}
