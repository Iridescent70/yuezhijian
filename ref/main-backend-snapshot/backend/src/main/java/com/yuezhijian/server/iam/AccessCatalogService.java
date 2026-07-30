package com.yuezhijian.server.iam;

import java.util.List;

public interface AccessCatalogService {
    String ROLE_ADMIN = "HEADQUARTERS_ADMIN";

    List<String> adminPermissions();

    List<StoreSummary> stores();

    List<RoleSummary> roles();

    List<MenuItem> menusForPermissions(List<String> permissions);

    UserIdentity userIdentity(String username);
}
