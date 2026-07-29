package com.yuezhijian.server.audit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryAuditRepository implements AuditRepository {
    private final AtomicLong ids = new AtomicLong();
    private final List<AuditLogRow> rows = new ArrayList<>();

    @Override
    public synchronized void append(NewAuditEvent event) {
        rows.add(new AuditLogRow(
                ids.incrementAndGet(), event.traceId(), event.userId(), "本地管理员", event.storeId(),
                event.module(), event.action(), event.objectType(), event.objectId(),
                event.beforeJson(), event.afterJson(), LocalDateTime.now()));
    }

    @Override
    public synchronized List<AuditLogRow> history(
            String objectType, String objectId, List<Long> accessibleStoreIds) {
        return rows.stream()
                .filter(row -> row.objectType().equals(objectType) && row.objectId().equals(objectId))
                .filter(row -> row.storeId() == null || accessibleStoreIds.contains(row.storeId()))
                .sorted(Comparator.comparing(AuditLogRow::occurredAt).reversed()
                        .thenComparing(Comparator.comparingLong(AuditLogRow::id).reversed()))
                .toList();
    }
}
