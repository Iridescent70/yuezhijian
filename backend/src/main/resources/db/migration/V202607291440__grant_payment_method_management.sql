-- 需求：系统管理-29、优化系统管理-02、API-CAT-019/020、UI-CAT-007
-- 目的：补齐支付方式全局维护、门店适用/启用/排序权限和并发版本。
-- 恢复：发布前可删除菜单和授权；共享环境执行后通过新Migration修复，不删除rowversion列。

ALTER TABLE dbo.cat_payment_method_store
    ADD row_version rowversion NOT NULL;

INSERT INTO dbo.iam_permission (
    permission_code, permission_name, resource_type, api_pattern, http_method
)
VALUES
    ('catalog:payment:view', N'查看支付方式配置', 'MENU', '/api/v1/payment-methods/**', 'GET'),
    ('catalog:payment:manage', N'维护支付方式定义', 'BUTTON', '/api/v1/payment-methods/**', 'POST,PUT'),
    ('catalog:payment:store-manage', N'维护门店支付配置', 'BUTTON', '/api/v1/payment-methods/**', 'PUT');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN (
      'catalog:payment:view', 'catalog:payment:manage', 'catalog:payment:store-manage'
  );

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'STORE_MANAGER'
  AND permission.permission_code IN ('catalog:payment:view', 'catalog:payment:store-manage');

INSERT INTO dbo.iam_menu (
    parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code
)
SELECT id, 'payment-methods', N'支付方式', '/app/system/payment-methods', 'Wallet', 65,
       'PC', 'catalog:payment:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

-- 验证：门店支付配置存在row_version，新增3项权限和1个菜单。
