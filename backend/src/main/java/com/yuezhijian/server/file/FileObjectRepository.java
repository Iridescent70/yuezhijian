package com.yuezhijian.server.file;

import java.util.List;
import java.util.Optional;

public interface FileObjectRepository {
    int countActive(String businessType, long businessId);

    List<BusinessAttachmentItem> attachments(String businessType, long businessId);

    Optional<StoredFileObject> findActive(String businessType, long businessId, long attachmentId);

    BusinessAttachmentItem createAndAttach(FileObjectDraft file, AttachmentDraft attachment);

    boolean softDelete(String businessType, long businessId, long attachmentId, long operatorId);
}
