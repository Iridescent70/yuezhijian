package com.yuezhijian.server.trade;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class TradeNumberGenerator {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicLong sequence = new AtomicLong(System.nanoTime() % 100_000);

    public String billNo() { return next("B"); }

    public String quoteNo() { return next("Q"); }

    public String paymentNo() { return next("P"); }

    public String discountBatchNo() { return next("D"); }

    private String next(String prefix) {
        return prefix + LocalDateTime.now().format(FORMAT)
                + String.format("%05d", Math.floorMod(sequence.incrementAndGet(), 100_000));
    }
}
