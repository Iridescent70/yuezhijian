package com.yuezhijian.server.file;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryFileObjectRepository implements FileObjectRepository {
    private final AtomicLong fileIds = new AtomicLong();
    private final AtomicLong attachmentIds = new AtomicLong();
    private final List<Entry> entries = new ArrayList<>();

    @Override
    public synchronized int countActive(String businessType, long businessId) {
        return (int) entries.stream().filter(item -> item.active()
                && item.businessType().equals(businessType) && item.businessId() == businessId).count();
    }

    @Override
    public synchronized List<BusinessAttachmentItem> attachments(String businessType, long businessId) {
        return entries.stream().filter(item -> item.active()
                        && item.businessType().equals(businessType) && item.businessId() == businessId)
                .map(Entry::item).toList();
    }

    @Override
    public synchronized Optional<StoredFileObject> findActive(
            String businessType, long businessId, long attachmentId) {
        return entries.stream().filter(item -> item.active() && item.item().id() == attachmentId
                        && item.businessType().equals(businessType) && item.businessId() == businessId)
                .map(item -> new StoredFileObject(
                        item.item().id(), item.item().fileId(), item.objectKey(), item.item().originalName(),
                        item.item().contentType(), item.item().sizeBytes(), item.item().sha256(),
                        item.item().purpose(), item.item().category()))
                .findFirst();
    }

    @Override
    public synchronized BusinessAttachmentItem createAndAttach(FileObjectDraft file, AttachmentDraft attachment) {
        long fileId = fileIds.incrementAndGet();
        long attachmentId = attachmentIds.incrementAndGet();
        BusinessAttachmentItem item = new BusinessAttachmentItem(
                attachmentId, fileId, file.originalName(), file.contentType(), file.sizeBytes(), file.sha256(),
                file.purpose(), attachment.category(), LocalDateTime.now(), attachment.operatorId(), "本地管理员");
        entries.add(new Entry(
                item, file.objectKey(), attachment.businessType(), attachment.businessId(), true));
        return item;
    }

    @Override
    public synchronized boolean softDelete(
            String businessType, long businessId, long attachmentId, long operatorId) {
        for (int index = 0; index < entries.size(); index++) {
            Entry current = entries.get(index);
            if (current.active() && current.item().id() == attachmentId
                    && current.businessType().equals(businessType) && current.businessId() == businessId) {
                entries.set(index, new Entry(
                        current.item(), current.objectKey(), current.businessType(), current.businessId(), false));
                return true;
            }
        }
        return false;
    }

    private record Entry(
            BusinessAttachmentItem item,
            String objectKey,
            String businessType,
            long businessId,
            boolean active) {
    }
}
