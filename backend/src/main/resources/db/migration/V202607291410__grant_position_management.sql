-- 需求：系统管理-10、API-ORG-006/007、UI-ORG-003
-- 目的：补齐职务状态约束、独立权限和系统菜单；职务业务字段已由V0910建立。
-- 影响：org_position、iam_permission、iam_role_permission、iam_menu。
-- 恢复：发布前可按菜单、角色权限、权限、约束的顺序删除；已授权后回退须先评估角色影响。

ALTER TABLE dbo.org_position WITH CHECK
ADD CONSTRAINT ck_org_position_status CHECK (status IN ('ACTIVE', 'DISABLED'));

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('org:position:view', N'查看职务', 'MENU', '/api/v1/positions/**', 'GET'),
    ('org:position:manage', N'维护职务', 'BUTTON', '/api/v1/positions/**', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN ('org:position:view', 'org:position:manage');

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'positions', N'职务管理', '/app/system/positions', 'Avatar', 35,
       'PC', 'org:position:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

-- 验证：状态约束1项、权限2项、总部管理员授权2项、职务菜单1项。
