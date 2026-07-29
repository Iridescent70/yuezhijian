-- 需求：优化会员管理-01、API-MEM-021、UI-MEM-001
-- 目的：将敏感会员名单导出从普通查看权限中拆出，单独授权和审计。

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES ('member:member:export', N'导出本门店会员名单', 'BUTTON', '/api/v1/exports', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code IN ('HEADQUARTERS_ADMIN', 'STORE_MANAGER')
  AND permission.permission_code = 'member:member:export';

-- 导出处理器固定使用任务创建时的当前门店，文件中的手机号保持脱敏。
