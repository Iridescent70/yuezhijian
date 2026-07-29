package com.yuezhijian.server.visit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class VisitNumberGenerator {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicLong sequence = new AtomicLong(System.nanoTime() % 100_000);

    public String taskNo() {
        return "VT" + LocalDateTime.now().format(FORMAT)
                + String.format("%05d", Math.floorMod(sequence.incrementAndGet(), 100_000));
    }
}
