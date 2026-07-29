package com.yuezhijian.server.masterdata;

public record ServiceImportOutcome(long serviceId, boolean created, String message) {
}
