-- 需求：系统管理-01、API-COM-004
-- 目的：记录异步任务结果文件的物理清理时间，保留任务与文件审计关系。

ALTER TABLE dbo.sys_async_job ADD result_purged_at datetime2(3) NULL;

CREATE INDEX ix_sys_async_job_result_cleanup
    ON dbo.sys_async_job (result_purged_at, expires_at, id)
    INCLUDE (status, result_file_id)
    WHERE result_file_id IS NOT NULL;

-- 清理顺序固定为：删除对象 -> 文件状态DELETED -> 写result_purged_at。
-- 任一步失败由下一轮任务幂等重试，不删除任务记录及result_file_id外键。
