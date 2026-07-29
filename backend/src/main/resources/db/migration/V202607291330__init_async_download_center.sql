-- 需求：系统管理-01、API-COM-003~005、UI-COM-001
-- 目的：把预留异步任务表落成按门店、创建人隔离的下载中心任务。

ALTER TABLE dbo.sys_async_job ADD
    job_name nvarchar(200) NULL,
    store_id bigint NULL,
    expires_at datetime2(3) NULL;

UPDATE dbo.sys_async_job
SET job_name = job_type,
    store_id = (SELECT TOP (1) id FROM dbo.org_store WHERE status = 'ACTIVE' ORDER BY id),
    expires_at = DATEADD(day, 7, created_at)
WHERE job_name IS NULL OR store_id IS NULL OR expires_at IS NULL;

ALTER TABLE dbo.sys_async_job ALTER COLUMN job_name nvarchar(200) NOT NULL;
ALTER TABLE dbo.sys_async_job ALTER COLUMN store_id bigint NOT NULL;
ALTER TABLE dbo.sys_async_job ALTER COLUMN expires_at datetime2(3) NOT NULL;

ALTER TABLE dbo.sys_async_job ADD CONSTRAINT fk_sys_async_job_store
    FOREIGN KEY (store_id) REFERENCES dbo.org_store(id);

CREATE INDEX ix_sys_async_job_creator_created
    ON dbo.sys_async_job (created_by, created_at DESC, id DESC)
    INCLUDE (job_type, status, progress, result_file_id, expires_at);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('system:job:view', N'查看和下载本人任务', 'MENU', '/api/v1/jobs/**', 'GET'),
    ('system:job:create', N'创建导出任务', 'BUTTON', '/api/v1/exports', 'POST'),
    ('system:job:cancel', N'取消本人等待任务', 'BUTTON', '/api/v1/jobs/**', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code IN ('HEADQUARTERS_ADMIN', 'STORE_MANAGER')
  AND permission.permission_code IN ('system:job:view', 'system:job:create', 'system:job:cancel');

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'download-center', N'下载中心', '/app/system/downloads', 'Download', 60,
       'PC', 'system:job:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

-- 下载接口必须同时校验任务创建人、任务状态、有效期和私有文件状态。
