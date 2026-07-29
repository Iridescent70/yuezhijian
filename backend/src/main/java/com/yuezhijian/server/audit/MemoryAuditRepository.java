package com.yuezhijian.server.audit;

import com.yuezhijian.server.common.PageResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
                event.beforeJson(), event.afterJson(), "SUCCESS", null, null, LocalDateTime.now()));
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

    @Override
    public synchronized PageResult<AuditLogRow> search(AuditLogQuery query) {
        List<AuditLogRow> matched = rows.stream()
                .filter(row -> query.userId() == null || query.userId().equals(row.userId()))
                .filter(row -> contains(row.operatorName(), query.operator()))
                .filter(row -> contains(row.module(), query.module()))
                .filter(row -> contains(row.action(), query.action()))
                .filter(row -> contains(row.objectType(), query.objectType()))
                .filter(row -> contains(row.objectId(), query.objectId()))
                .filter(row -> query.result() == null || row.result().equals(query.result()))
                .filter(row -> query.occurredFrom() == null || !row.occurredAt().isBefore(query.occurredFrom()))
                .filter(row -> query.occurredTo() == null || row.occurredAt().isBefore(query.occurredTo()))
                .sorted(Comparator.comparing(AuditLogRow::occurredAt).reversed()
                        .thenComparing(Comparator.comparingLong(AuditLogRow::id).reversed()))
                .toList();
        int from = Math.min(query.offset(), matched.size());
        int to = Math.min(from + query.size(), matched.size());
        return new PageResult<>(matched.subList(from, to), query.page(), query.size(), matched.size());
    }

    @Override
    public synchronized Optional<AuditLogRow> find(long id) {
        return rows.stream().filter(row -> row.id() == id).findFirst();
    }

    private boolean contains(String value, String filter) {
        return filter == null || value != null
                && value.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }
}
