package com.yuezhijian.server.iam;

import com.yuezhijian.server.common.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("sqlserver")
public class SqlServerAccessCatalogService implements AccessCatalogService {
    private final AccessCatalogMapper mapper;

    public SqlServerAccessCatalogService(AccessCatalogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<String> adminPermissions() {
        return mapper.findAllPermissionCodes();
    }

    @Override
    public List<StoreSummary> stores() {
        return mapper.findStores();
    }

    @Override
    public List<RoleSummary> roles() {
        return mapper.findRoles().stream()
                .map(row -> new RoleSummary(row.id(), row.code(), row.name(), row.dataScope(), row.status(),
                        mapper.findPermissionsByRoleId(row.id())))
                .toList();
    }

    @Override
    public List<MenuItem> menusForPermissions(List<String> permissions) {
        List<MenuRow> visibleRows = mapper.findMenus().stream()
                .filter(row -> row.permission() == null || permissions.contains(row.permission()))
                .toList();
        Map<Long, List<MenuRow>> childrenByParent = new HashMap<>();
        visibleRows.stream().filter(row -> row.parentId() != null)
                .forEach(row -> childrenByParent.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row));
        return visibleRows.stream()
                .filter(row -> row.parentId() == null)
                .map(row -> toMenu(row, childrenByParent))
                .toList();
    }

    @Override
    public UserIdentity userIdentity(String username) {
        AccessUserAccount account = mapper.findUserByUsername(username);
        if (account == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        Long storeId = account.currentStoreId();
        if (storeId == null) {
            storeId = stores().stream().findFirst().map(StoreSummary::id).orElse(null);
        }
        return new UserIdentity(account.id(), account.username(), account.fullName(), storeId);
    }

    private MenuItem toMenu(MenuRow row, Map<Long, List<MenuRow>> childrenByParent) {
        List<MenuItem> children = childrenByParent.getOrDefault(row.id(), List.of()).stream()
                .map(child -> toMenu(child, childrenByParent))
                .toList();
        return new MenuItem(row.id(), row.code(), row.name(), row.route(), row.icon(), row.sortNo(),
                row.permission(), children);
    }
}
