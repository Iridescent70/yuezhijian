package com.yuezhijian.server.colorstyle;

public record ColorStyleCategoryImageUpdate(
        long id,
        long imageFileId,
        String imageName,
        String imageContentType,
        String version,
        long operatorId) {
}
