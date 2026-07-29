package com.yuezhijian.server.colorstyle;

import java.time.LocalDateTime;

public record ColorStyleCategory(
        long id,
        Long parentId,
        String code,
        String name,
        Long imageFileId,
        String imageName,
        String imageContentType,
        int sortNo,
        String status,
        LocalDateTime updatedAt,
        Long updatedBy,
        String updatedByName,
        String version) {
}
