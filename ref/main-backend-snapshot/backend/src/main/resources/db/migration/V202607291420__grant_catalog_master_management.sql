-- 需求：系统管理-11/12/21、API-CAT-001/002/003、UI-CAT-002
-- 目的：为已有产品/服务分类和计量单位补齐维护权限及页面菜单。
-- 影响：iam_permission、iam_role_permission、iam_menu；不改变既有主数据。
-- 恢复：发布前可依次删除菜单、角色授权和权限；共享环境执行后通过新Migration修复。

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('catalog:master:view', N'查看分类与单位', 'MENU',
     '/api/v1/item-categories/**;/api/v1/units/**', 'GET'),
    ('catalog:master:manage', N'维护分类与单位', 'BUTTON',
     '/api/v1/item-categories/**;/api/v1/units/**', 'POST,PUT');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN ('catalog:master:view', 'catalog:master:manage');

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'catalog-master-data', N'分类与单位', '/app/catalog/units', 'CollectionTag', 15,
       'PC', 'catalog:master:view'
FROM dbo.iam_menu WHERE menu_code = 'catalog';

-- 验证：权限2项、总部管理员授权2项、分类与单位菜单1项。
