package com.yuezhijian.server.audit;

import java.util.List;

public interface AuditRepository {
    void append(NewAuditEvent event);

    List<AuditLogRow> history(String objectType, String objectId, List<Long> accessibleStoreIds);
}
