package com.yuezhijian.server.audit;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerAuditRepository implements AuditRepository {
    private final AuditMapper mapper;

    public SqlServerAuditRepository(AuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(NewAuditEvent event) {
        mapper.insert(event);
    }

    @Override
    public List<AuditLogRow> history(
            String objectType, String objectId, List<Long> accessibleStoreIds) {
        return mapper.findHistory(objectType, objectId, accessibleStoreIds);
    }
}
