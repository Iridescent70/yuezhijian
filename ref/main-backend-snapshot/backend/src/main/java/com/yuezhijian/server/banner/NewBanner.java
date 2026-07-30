package com.yuezhijian.server.banner;

import java.time.LocalDateTime;

public record NewBanner(
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
        long operatorId) {
}
