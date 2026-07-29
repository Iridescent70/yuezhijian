package com.yuezhijian.server.colorstyle;

public record NewColorStyleAsset(
        long colorStyleId,
        long fileId,
        String fileName,
        String contentType,
        int sortNo,
        long operatorId) {
}
