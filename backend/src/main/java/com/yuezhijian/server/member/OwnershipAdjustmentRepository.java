package com.yuezhijian.server.member;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OwnershipAdjustmentRepository {
    List<OwnershipAdjustmentRow> search(OwnershipAdjustmentQuery query);

    Optional<OwnershipAdjustmentRow> findById(long id);

    boolean hasActiveAdjustment(long memberId);

    OwnershipAdjustmentRow create(OwnershipAdjustmentDraft draft);

    OwnershipAdjustmentRow review(long id, boolean approved, String comment, String version, long operatorId);

    List<OwnershipAdjustmentRow> due(LocalDate businessDate);

    Optional<OwnershipAdjustmentRow> claim(long id, String version, LocalDate businessDate);

    OwnershipAdjustmentRow finish(long id, boolean applied, String message, String version);
}
