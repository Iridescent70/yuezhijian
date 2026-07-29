package com.yuezhijian.server.colorstyle;

import java.time.LocalDateTime;

public record ColorStyleAsset(
        long id,
        long colorStyleId,
        long fileId,
        String fileName,
        String contentType,
        int sortNo,
        String status,
        LocalDateTime updatedAt,
        String version) {
}
