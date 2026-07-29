package com.yuezhijian.server.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MemoryAsyncJobLeaseTest {
    @Test
    void anExpiredLeaseCanBeReclaimedAndTheOldWorkerLosesOwnership() {
        MemoryAsyncJobRepository repository = repositoryWithOneJob();
        AsyncJobTask first = repository.claimNext("worker-1", LocalDateTime.now().minusSeconds(1), 3)
                .orElseThrow();
        AsyncJobTask second = repository.claimNext("worker-2", LocalDateTime.now().plusMinutes(30), 3)
                .orElseThrow();

        assertThat(first.attemptCount()).isEqualTo(1);
        assertThat(second.attemptCount()).isEqualTo(2);
        assertThat(repository.renewLease(first.id(), first.leaseToken(), LocalDateTime.now().plusMinutes(30)))
                .isFalse();
        assertThat(repository.renewLease(second.id(), second.leaseToken(), LocalDateTime.now().plusMinutes(30)))
                .isTrue();
        repository.fail(first.id(), first.leaseToken(), "旧节点不应覆盖新节点");
        assertThat(repository.findOwned(first.id(), 1).orElseThrow().status()).isEqualTo("RUNNING");
    }

    @Test
    void anExpiredJobIsFailedAfterTheMaximumNumberOfClaims() {
        MemoryAsyncJobRepository repository = repositoryWithOneJob();
        for (int attempt = 0; attempt < 3; attempt++) {
            repository.claimNext(
                    "worker-" + attempt, LocalDateTime.now().minusSeconds(1), 3).orElseThrow();
        }

        assertThat(repository.failExhausted(3)).isEqualTo(1);
        AsyncJobItem item = repository.findOwned(1, 1).orElseThrow();
        assertThat(item.status()).isEqualTo("FAILED");
        assertThat(item.errorMessage()).contains("最大重试次数");
        assertThat(repository.claimNext("worker-4", LocalDateTime.now().plusMinutes(30), 3)).isEmpty();
    }

    private static MemoryAsyncJobRepository repositoryWithOneJob() {
        MemoryAsyncJobRepository repository = new MemoryAsyncJobRepository();
        repository.create(new AsyncJobDraft(
                "JOB-LEASE-1", "租约测试", "TEST", "{}", 1,
                LocalDateTime.now().plusDays(1), 1));
        return repository;
    }
}
