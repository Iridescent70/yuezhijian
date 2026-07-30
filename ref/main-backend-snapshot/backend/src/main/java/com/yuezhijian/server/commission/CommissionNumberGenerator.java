package com.yuezhijian.server.commission;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class CommissionNumberGenerator {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicLong sequence = new AtomicLong(System.nanoTime() % 100_000);

    public String ledgerNo() {
        return "CL" + LocalDateTime.now().format(FORMAT)
                + String.format("%05d", Math.floorMod(sequence.incrementAndGet(), 100_000));
    }
}
