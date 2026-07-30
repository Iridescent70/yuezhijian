package com.yuezhijian.server.banner;

public record BannerImageUpdate(
        long id,
        long imageFileId,
        String imageName,
        String imageContentType,
        String version,
        long operatorId) {
}
