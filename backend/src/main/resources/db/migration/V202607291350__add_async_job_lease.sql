-- 需求：系统管理-01、API-COM-003~005
-- 目的：为异步任务增加带所有权令牌的运行租约，支持执行节点宕机后的安全重领。

ALTER TABLE dbo.sys_async_job ADD
    lease_token varchar(36) NULL,
    lease_expires_at datetime2(3) NULL,
    attempt_count int NOT NULL CONSTRAINT df_sys_async_job_attempt_count DEFAULT (0);

ALTER TABLE dbo.sys_async_job ADD CONSTRAINT ck_sys_async_job_attempt_count
    CHECK (attempt_count BETWEEN 0 AND 10);

CREATE INDEX ix_sys_async_job_dispatch
    ON dbo.sys_async_job (status, lease_expires_at, created_at, id)
    INCLUDE (job_type, attempt_count);

-- 旧版本运行中的任务没有有效租约，升级后的首轮调度会将其视为失联任务并安全重领。
