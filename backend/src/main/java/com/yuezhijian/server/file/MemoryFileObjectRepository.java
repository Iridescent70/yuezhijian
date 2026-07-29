package com.yuezhijian.server.file;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryFileObjectRepository implements FileObjectRepository {
    private final AtomicLong fileIds = new AtomicLong();
    private final AtomicLong attachmentIds = new AtomicLong();
    private final Map<Long, FileEntry> files = new LinkedHashMap<>();
    private final List<AttachmentEntry> attachments = new ArrayList<>();

    @Override
    public synchronized FileObjectItem create(FileObjectDraft file) {
        return createFile(file).item();
    }

    @Override
    public synchronized int countActive(String businessType, long businessId) {
        return (int) attachments.stream().filter(item -> active(item)
                && item.businessType().equals(businessType) && item.businessId() == businessId).count();
    }

    @Override
    public synchronized List<BusinessAttachmentItem> attachments(String businessType, long businessId) {
        return attachments.stream().filter(item -> active(item)
                        && item.businessType().equals(businessType) && item.businessId() == businessId)
                .map(AttachmentEntry::item).toList();
    }

    @Override
    public synchronized Optional<StoredFileObject> findActive(
            String businessType, long businessId, long attachmentId) {
        return attachments.stream().filter(item -> active(item) && item.item().id() == attachmentId
                        && item.businessType().equals(businessType) && item.businessId() == businessId)
                .map(item -> storedFile(item.item().id(), files.get(item.item().fileId()), item.item().category()))
                .findFirst();
    }

    @Override
    public synchronized Optional<StoredFileObject> findActiveFile(long fileId) {
        FileEntry entry = files.get(fileId);
        if (entry == null || !entry.active()) return Optional.empty();
        return Optional.of(storedFile(0, entry, null));
    }

    @Override
    public synchronized boolean markJobFileDeleted(long fileId, String purpose) {
        FileEntry entry = files.get(fileId);
        if (entry == null || !entry.active() || !purpose.equals(entry.item().purpose())) return false;
        files.put(fileId, new FileEntry(entry.item(), entry.objectKey(), false));
        return true;
    }

    @Override
    public synchronized BusinessAttachmentItem createAndAttach(FileObjectDraft file, AttachmentDraft attachment) {
        FileEntry fileEntry = createFile(file);
        long attachmentId = attachmentIds.incrementAndGet();
        BusinessAttachmentItem item = new BusinessAttachmentItem(
                attachmentId, fileEntry.item().id(), file.originalName(), file.contentType(), file.sizeBytes(),
                file.sha256(), file.purpose(), attachment.category(), LocalDateTime.now(),
                attachment.operatorId(), "本地管理员");
        attachments.add(new AttachmentEntry(
                item, attachment.businessType(), attachment.businessId(), true));
        return item;
    }

    @Override
    public synchronized boolean softDelete(
            String businessType, long businessId, long attachmentId, long operatorId) {
        for (int index = 0; index < attachments.size(); index++) {
            AttachmentEntry current = attachments.get(index);
            if (active(current) && current.item().id() == attachmentId
                    && current.businessType().equals(businessType) && current.businessId() == businessId) {
                attachments.set(index, new AttachmentEntry(
                        current.item(), current.businessType(), current.businessId(), false));
                FileEntry file = files.get(current.item().fileId());
                files.put(file.item().id(), new FileEntry(file.item(), file.objectKey(), false));
                return true;
            }
        }
        return false;
    }

    private FileEntry createFile(FileObjectDraft draft) {
        long fileId = fileIds.incrementAndGet();
        FileObjectItem item = new FileObjectItem(
                fileId, draft.originalName(), draft.contentType(), draft.sizeBytes(), draft.sha256(),
                draft.purpose(), LocalDateTime.now(), draft.ownerUserId());
        FileEntry entry = new FileEntry(item, draft.objectKey(), true);
        files.put(fileId, entry);
        return entry;
    }

    private boolean active(AttachmentEntry attachment) {
        FileEntry file = files.get(attachment.item().fileId());
        return attachment.active() && file != null && file.active();
    }

    private StoredFileObject storedFile(long attachmentId, FileEntry entry, String category) {
        FileObjectItem item = entry.item();
        return new StoredFileObject(
                attachmentId, item.id(), entry.objectKey(), item.originalName(), item.contentType(),
                item.sizeBytes(), item.sha256(), item.purpose(), category);
    }

    private record FileEntry(FileObjectItem item, String objectKey, boolean active) {
    }

    private record AttachmentEntry(
            BusinessAttachmentItem item,
            String businessType,
            long businessId,
            boolean active) {
    }
}
