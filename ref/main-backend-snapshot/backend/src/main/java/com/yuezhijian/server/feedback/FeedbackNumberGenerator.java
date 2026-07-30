package com.yuezhijian.server.feedback;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class FeedbackNumberGenerator {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicLong sequence = new AtomicLong(System.nanoTime() % 100_000);

    public String feedbackNo() {
        return "FB" + LocalDateTime.now().format(FORMAT)
                + String.format("%05d", Math.floorMod(sequence.incrementAndGet(), 100_000));
    }
}
