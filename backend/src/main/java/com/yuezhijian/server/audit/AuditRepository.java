package com.yuezhijian.server.audit;

import com.yuezhijian.server.common.PageResult;
import java.util.List;
import java.util.Optional;

public interface AuditRepository {
    void append(NewAuditEvent event);

    List<AuditLogRow> history(String objectType, String objectId, List<Long> accessibleStoreIds);

    PageResult<AuditLogRow> search(AuditLogQuery query);

    Optional<AuditLogRow> find(long id);
}
