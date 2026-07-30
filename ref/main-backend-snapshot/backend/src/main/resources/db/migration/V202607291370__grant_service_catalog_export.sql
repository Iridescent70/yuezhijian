-- 需求：系统管理-09、API-CAT-003
-- 目的：将服务项目导出从查看和维护权限中拆出，避免普通查看者批量带走价格资料。

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES ('catalog:service:export', N'导出当前门店服务项目', 'BUTTON', '/api/v1/exports', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code = 'catalog:service:export';

-- 导出处理器固定使用任务创建时的当前门店，不接受前端传入任意门店范围。
