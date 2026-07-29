package com.yuezhijian.server.banner;

import java.time.LocalDateTime;

public record Banner(
        long id,
        String positionCode,
        String title,
        long imageFileId,
        String imageName,
        String imageContentType,
        String linkType,
        String linkValue,
        int sortNo,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String status,
        LocalDateTime updatedAt,
        Long updatedBy,
        String updatedByName,
        String version) {
}
