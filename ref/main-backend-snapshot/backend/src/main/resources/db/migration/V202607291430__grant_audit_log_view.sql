-- 需求：系统管理-07、API-COM-006/007、UI-IAM-005
-- 目的：开放统一操作日志查询权限和系统管理菜单。
-- 影响：iam_permission、iam_role_permission、iam_menu；不改变审计业务数据。
-- 恢复：发布前可依次删除菜单、角色授权和权限；共享环境执行后通过新Migration修复。

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES ('system:audit:view', N'查看操作日志', 'MENU', '/api/v1/audit-logs/**', 'GET');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code = 'system:audit:view';

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'audit-logs', N'操作日志', '/app/system/audit-logs', 'Document', 70,
       'PC', 'system:audit:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

-- 验证：权限1项、总部管理员授权1项、操作日志菜单1项。
