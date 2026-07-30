package com.yuezhijian.server.cancelreason;

import java.util.List;
import java.util.Optional;

public interface CancelReasonRepository {
    List<CancelReason> findAll(String businessType, String keyword, String status);

    Optional<CancelReason> find(long id);

    Optional<CancelReason> findActive(String businessType, String code);

    CancelReason create(NewCancelReason reason);

    CancelReason update(CancelReasonUpdate update);
}
