package com.yuezhijian.server.banner;

import java.time.LocalDateTime;

public record BannerUpdate(
        long id,
        String positionCode,
        String title,
        String linkType,
        String linkValue,
        int sortNo,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String status,
        String version,
        long operatorId) {
}
