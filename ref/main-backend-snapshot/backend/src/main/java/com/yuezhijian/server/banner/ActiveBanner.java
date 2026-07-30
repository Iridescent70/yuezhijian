package com.yuezhijian.server.banner;

public record ActiveBanner(
        long id,
        String title,
        String linkType,
        String linkValue,
        int sortNo,
        String version) {
}
