package com.yuezhijian.server.member;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class BusinessNumberGenerator {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicLong sequence = new AtomicLong(System.nanoTime() % 10_000);
    private final Clock clock = Clock.systemDefaultZone();

    public String nextMemberNo() {
        return next("M");
    }

    public String nextMembershipCardNo() {
        return next("C");
    }

    private String next(String prefix) {
        long suffix = Math.floorMod(sequence.incrementAndGet(), 100_000);
        return prefix + LocalDateTime.now(clock).format(TIME_FORMAT) + String.format("%05d", suffix);
    }
}
