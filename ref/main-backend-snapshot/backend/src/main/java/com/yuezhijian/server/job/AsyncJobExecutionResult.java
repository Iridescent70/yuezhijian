package com.yuezhijian.server.job;

public record AsyncJobExecutionResult(
        String fileName,
        String contentType,
        byte[] content,
        int successCount,
        int failureCount) {
    public AsyncJobExecutionResult {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
