package com.yuezhijian.server.iam;

public record UserIdentity(long id, String username, String fullName, Long currentStoreId) {
}
