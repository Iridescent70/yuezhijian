-- 需求：优化系统管理-01、API-CAT-011、UI-COM-08
-- 目的：异步导入任务只保存私有输入文件引用，避免把原始CSV写入任务JSON。

ALTER TABLE dbo.sys_async_job ADD input_file_id bigint NULL;
GO

ALTER TABLE dbo.sys_async_job ADD CONSTRAINT fk_sys_async_job_input_file
    FOREIGN KEY (input_file_id) REFERENCES dbo.sys_file_object(id);

CREATE INDEX ix_sys_async_job_input_file
    ON dbo.sys_async_job (input_file_id)
    WHERE input_file_id IS NOT NULL;
