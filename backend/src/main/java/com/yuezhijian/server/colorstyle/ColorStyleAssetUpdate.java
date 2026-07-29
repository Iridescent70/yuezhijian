package com.yuezhijian.server.colorstyle;

public record ColorStyleAssetUpdate(
        long id,
        long colorStyleId,
        int sortNo,
        String status,
        String version,
        long operatorId) {
}
