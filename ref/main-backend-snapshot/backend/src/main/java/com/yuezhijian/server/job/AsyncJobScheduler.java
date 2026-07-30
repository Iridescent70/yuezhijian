package com.yuezhijian.server.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AsyncJobScheduler {
    private final AsyncJobService service;

    public AsyncJobScheduler(AsyncJobService service) {
        this.service = service;
    }

    @Scheduled(
            initialDelayString = "${app.jobs.initial-delay-ms:3000}",
            fixedDelayString = "${app.jobs.poll-delay-ms:2000}")
    public void processPending() {
        for (int count = 0; count < 5 && service.processNext(); count++) {
            // 每轮最多领取5项，避免任务高峰长期占用调度线程。
        }
    }

    @Scheduled(
            initialDelayString = "${app.jobs.cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${app.jobs.cleanup-delay-ms:3600000}")
    public void cleanupExpiredResults() {
        service.cleanupExpiredResults();
    }
}
