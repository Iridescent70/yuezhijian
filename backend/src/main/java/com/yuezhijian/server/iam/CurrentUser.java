package com.yuezhijian.server.iam;

import java.util.List;

public record CurrentUser(
        long id,
        String username,
        String fullName,
        long currentStoreId,
        String currentStoreName,
        List<String> roles,
        List<String> permissions,
        List<StoreSummary> stores,
        List<MenuItem> menus) {
}
