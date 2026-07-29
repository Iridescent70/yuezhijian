package com.yuezhijian.server.job;

public record ExpiredJobResult(long jobId, long fileId) {
}
