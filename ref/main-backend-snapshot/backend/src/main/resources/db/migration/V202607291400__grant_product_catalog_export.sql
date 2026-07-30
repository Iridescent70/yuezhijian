-- 需求：系统管理-11、API-CAT-007、UI-CAT-001
-- 目的：产品资料导出使用独立权限，避免查看权限自动获得成本导出能力。

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES ('catalog:product:export', N'导出当前门店产品资料', 'BUTTON', '/api/v1/exports', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code = 'catalog:product:export';

-- 导出处理器固定使用任务创建时的当前门店，不接受前端传入任意门店范围。
