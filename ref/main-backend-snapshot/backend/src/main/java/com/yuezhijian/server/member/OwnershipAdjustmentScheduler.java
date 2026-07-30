package com.yuezhijian.server.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("sqlserver")
public class OwnershipAdjustmentScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(OwnershipAdjustmentScheduler.class);
    private final OwnershipAdjustmentService service;

    public OwnershipAdjustmentScheduler(OwnershipAdjustmentService service) {
        this.service = service;
    }

    @Scheduled(
            initialDelayString = "${app.ownership.initial-delay-ms:30000}",
            fixedDelayString = "${app.ownership.apply-delay-ms:60000}")
    public void applyDueAdjustments() {
        int applied = service.applyDueOwnershipChanges();
        if (applied > 0) LOG.info("Applied {} due member ownership adjustments", applied);
    }
}
