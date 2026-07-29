package com.yuezhijian.server.file;

public record AttachmentDraft(
        String businessType,
        long businessId,
        long storeId,
        String category,
        long operatorId) {
}
