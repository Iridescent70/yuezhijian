package com.yuezhijian.server.job;

public record AsyncJobQuery(
        long createdBy,
        String jobType,
        String status,
        int page,
        int size) {
    public int offset() {
        return (page - 1) * size;
    }
}
