package com.yuezhijian.server.file;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlserver")
public class SqlServerFileObjectRepository implements FileObjectRepository {
    private final FileObjectMapper mapper;

    public SqlServerFileObjectRepository(FileObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FileObjectItem create(FileObjectDraft file) {
        return mapper.findFileItem(mapper.insertFileObject(file));
    }

    @Override
    public int countActive(String businessType, long businessId) {
        return mapper.countActive(businessType, businessId);
    }

    @Override
    public List<BusinessAttachmentItem> attachments(String businessType, long businessId) {
        return mapper.findAttachments(businessType, businessId);
    }

    @Override
    public Optional<StoredFileObject> findActive(String businessType, long businessId, long attachmentId) {
        return Optional.ofNullable(mapper.findActive(businessType, businessId, attachmentId));
    }

    @Override
    public Optional<StoredFileObject> findActiveFile(long fileId) {
        return Optional.ofNullable(mapper.findActiveFile(fileId));
    }

    @Override
    public boolean markFileDeleted(long fileId, String purpose) {
        return mapper.markFileDeleted(fileId, purpose) == 1;
    }

    @Override
    @Transactional
    public BusinessAttachmentItem createAndAttach(FileObjectDraft file, AttachmentDraft attachment) {
        long fileId = mapper.insertFileObject(file);
        long attachmentId = mapper.insertAttachment(fileId, attachment);
        return mapper.findAttachments(attachment.businessType(), attachment.businessId()).stream()
                .filter(item -> item.id() == attachmentId).findFirst().orElseThrow();
    }

    @Override
    @Transactional
    public boolean softDelete(String businessType, long businessId, long attachmentId, long operatorId) {
        if (mapper.softDeleteAttachment(businessType, businessId, attachmentId, operatorId) != 1) return false;
        if (mapper.softDeleteFile(attachmentId, operatorId) != 1) {
            throw new DuplicateResourceException("附件状态已发生变化，请刷新后重试");
        }
        return true;
    }
}
