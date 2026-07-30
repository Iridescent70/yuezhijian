package com.yuezhijian.server.common;

import java.util.UUID;
import org.slf4j.MDC;

public final class TraceIds {
    public static final String MDC_KEY = "traceId";

    private TraceIds() {
    }

    public static String current() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null ? "" : traceId;
    }

    public static String next() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
