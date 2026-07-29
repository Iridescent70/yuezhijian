package com.yuezhijian.server.iam;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("memory")
public class MemoryAccessCatalogService implements AccessCatalogService {
    private static final List<String> ADMIN_PERMISSIONS = List.of(
            "workbench:view", "org:store:view", "iam:role:view", "iam:user:view",
            "member:member:view", "member:member:create",
            "org:employee:view", "org:employee:manage",
            "org:workstation:view", "org:workstation:manage",
            "catalog:service:view", "catalog:service:manage",
            "appointment:appointment:view", "appointment:appointment:create",
            "trade:bill:view", "trade:bill:create");

    private static final List<StoreSummary> STORES = List.of(
            new StoreSummary(1L, "HQ", "悦指间总部", "HEADQUARTERS", "ACTIVE"),
            new StoreSummary(2L, "S001", "悦指间示范店", "A", "ACTIVE"));

    private static final List<RoleSummary> ROLES = List.of(
            new RoleSummary(1L, ROLE_ADMIN, "总部管理员", "ALL", "ACTIVE", ADMIN_PERMISSIONS),
            new RoleSummary(2L, "STORE_MANAGER", "店长", "STORE", "ACTIVE",
                    List.of("workbench:view", "org:store:view", "member:member:view", "trade:bill:view")));

    private static final List<MenuItem> MENUS = List.of(
            new MenuItem(1L, "workbench", "工作台", "/app/workbench", "HomeFilled", 10,
                    "workbench:view", List.of()),
            new MenuItem(2L, "member", "会员管理", "/app/members", "User", 20,
                    "member:member:view", List.of()),
            new MenuItem(3L, "appointment", "预约管理", "/app/appointments", "Calendar", 30,
                    "appointment:appointment:view", List.of()),
            new MenuItem(4L, "bill", "账单管理", "/app/bills", "Tickets", 40,
                    "trade:bill:view", List.of()),
            new MenuItem(5L, "system", "系统管理", "/app/system", "Setting", 90, null,
                    List.of(
                            new MenuItem(51L, "stores", "组织门店", "/app/system/stores", "Shop", 10,
                                    "org:store:view", List.of()),
                            new MenuItem(52L, "roles", "角色管理", "/app/system/roles", "Lock", 20,
                                    "iam:role:view", List.of()),
                            new MenuItem(53L, "employees", "员工管理", "/app/system/employees", "UserFilled", 30,
                                    "org:employee:view", List.of()),
                            new MenuItem(54L, "workstations", "工位管理", "/app/system/workstations", "OfficeBuilding", 40,
                                    "org:workstation:view", List.of()))),
            new MenuItem(6L, "catalog", "基础资料", "/app/catalog", "Collection", 60, null,
                    List.of(new MenuItem(61L, "services", "服务项目", "/app/catalog/services", "Service", 10,
                            "catalog:service:view", List.of()))));

    @Override
    public List<String> adminPermissions() {
        return ADMIN_PERMISSIONS;
    }

    @Override
    public List<StoreSummary> stores() {
        return STORES;
    }

    @Override
    public List<RoleSummary> roles() {
        return ROLES;
    }

    @Override
    public List<MenuItem> menusForPermissions(List<String> permissions) {
        return MENUS.stream().map(menu -> filterMenu(menu, permissions)).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public UserIdentity userIdentity(String username) {
        return new UserIdentity(1L, username, "本地管理员", STORES.getFirst().id());
    }

    private MenuItem filterMenu(MenuItem menu, List<String> permissions) {
        List<MenuItem> visibleChildren = menu.children().stream()
                .map(child -> filterMenu(child, permissions))
                .filter(java.util.Objects::nonNull)
                .toList();
        boolean ownPermissionGranted = menu.permission() == null || permissions.contains(menu.permission());
        if (!ownPermissionGranted && visibleChildren.isEmpty()) {
            return null;
        }
        return new MenuItem(menu.id(), menu.code(), menu.name(), menu.route(), menu.icon(), menu.sortNo(),
                menu.permission(), visibleChildren);
    }
}
