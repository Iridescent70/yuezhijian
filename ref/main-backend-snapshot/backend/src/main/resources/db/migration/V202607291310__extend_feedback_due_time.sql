-- 需求：系统管理-20、结算管理-05、UI-VIS-002
-- 目的：服务反馈保存处理时限快照，查询时实时识别未解决的超时记录。

INSERT INTO dbo.sys_parameter (
    param_group, param_key, value_ciphertext, value_type, is_secret, description
)
VALUES (
    'VISIT', 'SERVICE_FEEDBACK_DUE_HOURS', N'24', 'INTEGER', 0,
    N'服务反馈创建或重新打开后多少小时应完成处理'
);

ALTER TABLE dbo.vis_feedback ADD
    due_hours int NOT NULL CONSTRAINT df_vis_feedback_due_hours DEFAULT (24),
    due_at datetime2(3) NULL;
GO

UPDATE dbo.vis_feedback
SET due_at = DATEADD(HOUR, due_hours, created_at)
WHERE due_at IS NULL;

ALTER TABLE dbo.vis_feedback ALTER COLUMN due_at datetime2(3) NOT NULL;

ALTER TABLE dbo.vis_feedback ADD CONSTRAINT ck_vis_feedback_due_hours
    CHECK (due_hours BETWEEN 1 AND 720);

ALTER TABLE dbo.vis_feedback ADD CONSTRAINT ck_vis_feedback_due_at
    CHECK (due_at >= created_at);

CREATE INDEX ix_vis_feedback_due_queue
    ON dbo.vis_feedback (status, due_at, store_id, id);

-- 24小时是可维护的技术默认值，甲方可在系统参数页修改；历史记录保留自己的快照。
