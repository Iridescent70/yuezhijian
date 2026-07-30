package com.yuezhijian.server.iam;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("memory")
public class MemoryAccessCatalogService implements AccessCatalogService {
    private static final List<String> ADMIN_PERMISSIONS = List.of(
            "workbench:view", "org:store:view", "iam:role:view", "iam:user:view",
            "member:member:view", "member:member:create", "member:member:manage",
            "member:member:export",
            "member:tag:view", "member:tag:manage",
            "member:ownership:view", "member:ownership:manage", "member:ownership:approve",
            "member:asset:view", "member:asset:manage",
            "catalog:card:view", "catalog:card:manage", "member:card:view", "member:card:manage",
            "member:card:refund:view", "member:card:refund:manage", "member:card:refund:approve",
            "benefit:voucher:view", "benefit:voucher:manage", "benefit:voucher:issue",
            "commission:plan:view", "commission:plan:manage", "commission:ledger:view",
            "visit:task:view", "visit:task:manage", "visit:feedback:view", "visit:feedback:manage",
            "visit:satisfaction:view", "visit:satisfaction:manage",
            "system:parameter:view", "system:parameter:manage",
            "system:audit:view",
            "system:cancel-reason:view", "system:cancel-reason:manage",
            "system:banner:view", "system:banner:manage",
            "system:color-style:view", "system:color-style:manage",
            "notification:view",
            "system:announcement:view", "system:announcement:manage",
            "system:notification-template:view", "system:notification-template:manage",
            "inventory:gift:view", "inventory:gift:manage", "inventory:stock:view",
            "inventory:transfer:view", "inventory:transfer:manage",
            "inventory:count:view", "inventory:count:manage",
            "system:job:view", "system:job:create", "system:job:cancel",
            "org:employee:view", "org:employee:manage",
            "org:position:view", "org:position:manage",
            "org:workstation:view", "org:workstation:manage",
            "catalog:service:view", "catalog:service:manage", "catalog:service:export",
            "catalog:product:view", "catalog:product:manage", "catalog:product:export",
            "catalog:payment:view", "catalog:payment:manage", "catalog:payment:store-manage",
            "home:service-area:view", "home:service-area:manage",
            "catalog:master:view", "catalog:master:manage",
            "appointment:appointment:view", "appointment:appointment:create", "appointment:appointment:manage",
            "trade:bill:view", "trade:bill:create", "trade:bill:manage", "trade:bill:settle",
            "trade:reversal:view", "trade:reversal:manage", "trade:reversal:approve");

    private static final List<StoreSummary> STORES = List.of(
            new StoreSummary(1L, "HQ", "悦指间总部", "HEADQUARTERS", "ACTIVE"),
            new StoreSummary(2L, "S001", "悦指间示范店", "A", "ACTIVE"));

    private static final List<RoleSummary> ROLES = List.of(
            new RoleSummary(1L, ROLE_ADMIN, "总部管理员", "ALL", "ACTIVE", ADMIN_PERMISSIONS),
            new RoleSummary(2L, "STORE_MANAGER", "店长", "STORE", "ACTIVE",
                    List.of("workbench:view", "org:store:view", "member:member:view", "member:member:manage",
                            "member:member:export",
                            "member:tag:view", "member:tag:manage", "member:ownership:view",
                            "member:ownership:manage", "trade:bill:view",
                            "notification:view",
                            "inventory:gift:view", "inventory:stock:view", "inventory:transfer:view",
                            "inventory:count:view", "inventory:count:manage",
                            "home:service-area:view", "home:service-area:manage",
                            "system:job:view", "system:job:create", "system:job:cancel")));

    private static final List<MenuItem> MENUS = List.of(
            new MenuItem(1L, "workbench", "工作台", "/app/workbench", "HomeFilled", 10,
                    "workbench:view", List.of()),
            new MenuItem(2L, "member", "会员管理", "/app/members", "User", 20,
                    "member:member:view", List.of(
                            new MenuItem(201L, "member-list", "会员列表", "/app/members", "List", 10,
                                    "member:member:view", List.of()),
                            new MenuItem(202L, "member-ownership", "归属调整", "/app/members/ownership", "Switch", 20,
                                    "member:ownership:view", List.of()))),
            new MenuItem(3L, "appointment", "预约管理", "/app/appointments", "Calendar", 30,
                    "appointment:appointment:view", List.of()),
            new MenuItem(4L, "bill", "账单管理", "/app/bills", "Tickets", 40,
                    "trade:bill:view", List.of()),
            new MenuItem(7L, "reversals", "冲销管理", "/app/settlement/reversals", "RefreshLeft", 50,
                    "trade:reversal:view", List.of()),
            new MenuItem(8L, "card-refunds", "退卡管理", "/app/assets/card-refunds", "CreditCard", 55,
                    "member:card:refund:view", List.of()),
            new MenuItem(9L, "vouchers", "代金券管理", "/app/benefits/vouchers", "Ticket", 58,
                    "benefit:voucher:view", List.of()),
            new MenuItem(10L, "commission", "薪酬提成", "/app/commission", "Money", 60, null,
                    List.of(
                            new MenuItem(101L, "commission-plans", "提成方案", "/app/commission/plans", "SetUp", 10,
                                    "commission:plan:view", List.of()),
                            new MenuItem(103L, "commission-simulator", "薪资测算", "/app/commission/simulator", "DataAnalysis", 20,
                                    "commission:plan:view", List.of()),
                            new MenuItem(102L, "commission-ledgers", "提成流水", "/app/commission/ledgers", "List", 30,
                                    "commission:ledger:view", List.of()))),
            new MenuItem(11L, "service-center", "客户服务", "/app/service", "Service", 65, null,
                    List.of(
                            new MenuItem(111L, "visit-tasks", "回访管理", "/app/service/visits", "ChatLineSquare", 10,
                                    "visit:task:view", List.of()),
                            new MenuItem(112L, "service-feedback", "服务反馈", "/app/service/feedback", "Warning", 20,
                                    "visit:feedback:view", List.of()),
                            new MenuItem(113L, "satisfaction-rules", "满意度规则", "/app/service/satisfaction-rules", "Star", 30,
                                    "visit:satisfaction:view", List.of()))),
            new MenuItem(12L, "notifications", "消息中心", "/app/notifications", "Bell", 70,
                    "notification:view", List.of()),
            new MenuItem(13L, "inventory", "礼品库存", "/app/inventory/gifts", "Box", 62, null,
                    List.of(
                            new MenuItem(131L, "gift-inventory", "礼品库存", "/app/inventory/gifts", "Goods", 10,
                                    "inventory:stock:view", List.of()),
                            new MenuItem(132L, "inventory-transfers", "礼品调拨", "/app/inventory/transfers", "Switch", 20,
                                    "inventory:transfer:view", List.of()),
                            new MenuItem(133L, "inventory-counts", "礼品盘点", "/app/inventory/counts", "Checked", 30,
                                    "inventory:count:view", List.of()))),
            new MenuItem(5L, "system", "系统管理", "/app/system", "Setting", 90, null,
                    List.of(
                            new MenuItem(51L, "stores", "组织门店", "/app/system/stores", "Shop", 10,
                                    "org:store:view", List.of()),
                            new MenuItem(52L, "roles", "角色管理", "/app/system/roles", "Lock", 20,
                                    "iam:role:view", List.of()),
                            new MenuItem(53L, "employees", "员工管理", "/app/system/employees", "UserFilled", 30,
                                    "org:employee:view", List.of()),
                            new MenuItem(57L, "positions", "职务管理", "/app/system/positions", "Avatar", 35,
                                    "org:position:view", List.of()),
                            new MenuItem(54L, "workstations", "工位管理", "/app/system/workstations", "OfficeBuilding", 40,
                                    "org:workstation:view", List.of()),
                            new MenuItem(55L, "system-parameters", "系统参数", "/app/system/parameters", "Operation", 50,
                                    "system:parameter:view", List.of()),
                            new MenuItem(56L, "download-center", "下载中心", "/app/system/downloads", "Download", 60,
                                    "system:job:view", List.of()),
                            new MenuItem(59L, "payment-methods", "支付方式", "/app/system/payment-methods", "Wallet", 65,
                                    "catalog:payment:view", List.of()),
                            new MenuItem(60L, "service-areas", "服务小区", "/app/system/service-areas", "Location", 67,
                                    "home:service-area:view", List.of()),
                            new MenuItem(65L, "cancel-reasons", "取消原因", "/app/system/cancel-reasons", "CircleClose", 68,
                                    "system:cancel-reason:view", List.of()),
                            new MenuItem(66L, "banners", "首页图片", "/app/system/banners", "Picture", 69,
                                    "system:banner:view", List.of()),
                            new MenuItem(67L, "color-styles", "线上试色", "/app/system/color-styles", "Brush", 70,
                                    "system:color-style:view", List.of()),
                            new MenuItem(68L, "announcements", "通知公告", "/app/system/announcements", "Notification", 72,
                                    "system:announcement:view", List.of()),
                            new MenuItem(69L, "notification-templates", "通知模板", "/app/system/notification-templates", "ChatLineSquare", 74,
                                    "system:notification-template:view", List.of()),
                            new MenuItem(58L, "audit-logs", "操作日志", "/app/system/audit-logs", "Document", 85,
                                    "system:audit:view", List.of()))),
            new MenuItem(6L, "catalog", "基础资料", "/app/catalog", "Collection", 60, null,
                    List.of(
                            new MenuItem(63L, "products", "产品管理", "/app/catalog/products", "Goods", 5,
                                    "catalog:product:view", List.of()),
                            new MenuItem(61L, "services", "服务项目", "/app/catalog/services", "Service", 10,
                                    "catalog:service:view", List.of()),
                            new MenuItem(64L, "catalog-master-data", "分类与单位", "/app/catalog/units", "CollectionTag", 15,
                                    "catalog:master:view", List.of()),
                            new MenuItem(62L, "card-types", "次卡类型", "/app/catalog/card-types", "Postcard", 20,
                                    "catalog:card:view", List.of()))));

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
