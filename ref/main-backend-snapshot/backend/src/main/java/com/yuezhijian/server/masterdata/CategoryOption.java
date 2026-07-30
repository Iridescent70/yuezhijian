package com.yuezhijian.server.masterdata;

public record CategoryOption(
        long id,
        String code,
        String name,
        String type,
        int sortNo,
        String status,
        String version) {
}
