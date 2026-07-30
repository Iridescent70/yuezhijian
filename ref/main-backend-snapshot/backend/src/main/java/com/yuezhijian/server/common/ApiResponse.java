package com.yuezhijian.server.common;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        OffsetDateTime serverTime) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("0", "OK", data, TraceIds.current(), OffsetDateTime.now());
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null, TraceIds.current(), OffsetDateTime.now());
    }
}
