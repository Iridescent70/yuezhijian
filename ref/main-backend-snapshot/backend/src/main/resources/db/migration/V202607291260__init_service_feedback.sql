-- 需求：系统管理-20、结算管理-05、API-VIS-006~007、UI-VIS-002
-- 目的：将回访客诉标记转为独立反馈单，记录负责人、处理过程、解决和关闭状态。

CREATE TABLE dbo.vis_feedback (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_vis_feedback PRIMARY KEY,
    feedback_no varchar(32) NOT NULL,
    visit_task_id bigint NOT NULL,
    visit_record_id bigint NOT NULL,
    member_id bigint NOT NULL,
    bill_id bigint NOT NULL,
    store_id bigint NOT NULL,
    channel varchar(24) NOT NULL,
    score tinyint NULL,
    content nvarchar(2000) NOT NULL,
    complaint_type varchar(64) NOT NULL,
    status varchar(16) NOT NULL CONSTRAINT df_vis_feedback_status DEFAULT ('OPEN'),
    handler_id bigint NULL,
    handle_result nvarchar(2000) NULL,
    handled_at datetime2(3) NULL,
    resolved_at datetime2(3) NULL,
    closed_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_vis_feedback_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_vis_feedback_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_vis_feedback_no UNIQUE (feedback_no),
    CONSTRAINT uq_vis_feedback_record UNIQUE (visit_record_id),
    CONSTRAINT fk_vis_feedback_task FOREIGN KEY (visit_task_id) REFERENCES dbo.vis_visit_task(id),
    CONSTRAINT fk_vis_feedback_record FOREIGN KEY (visit_record_id) REFERENCES dbo.vis_visit_record(id),
    CONSTRAINT fk_vis_feedback_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_vis_feedback_bill FOREIGN KEY (bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT fk_vis_feedback_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_vis_feedback_handler FOREIGN KEY (handler_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_vis_feedback_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_vis_feedback_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_vis_feedback_channel CHECK (channel IN ('VISIT')),
    CONSTRAINT ck_vis_feedback_score CHECK (score IS NULL OR score BETWEEN 1 AND 5),
    CONSTRAINT ck_vis_feedback_type CHECK (complaint_type IN ('SERVICE')),
    CONSTRAINT ck_vis_feedback_status CHECK (status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_vis_feedback_handler CHECK (status = 'OPEN' OR handler_id IS NOT NULL),
    CONSTRAINT ck_vis_feedback_resolution CHECK (
        (status = 'RESOLVED' AND resolved_at IS NOT NULL AND handle_result IS NOT NULL)
        OR (status = 'CLOSED' AND resolved_at IS NOT NULL AND closed_at IS NOT NULL AND handle_result IS NOT NULL)
        OR status IN ('OPEN', 'PROCESSING')
    )
);

CREATE INDEX ix_vis_feedback_store_status
    ON dbo.vis_feedback (store_id, status, updated_at DESC, id DESC);
CREATE INDEX ix_vis_feedback_handler_status
    ON dbo.vis_feedback (handler_id, status, updated_at DESC)
    WHERE handler_id IS NOT NULL;
CREATE INDEX ix_vis_feedback_member
    ON dbo.vis_feedback (member_id, created_at DESC);

CREATE TABLE dbo.vis_feedback_action (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_vis_feedback_action PRIMARY KEY,
    feedback_id bigint NOT NULL,
    action_type varchar(24) NOT NULL,
    from_status varchar(16) NULL,
    to_status varchar(16) NOT NULL,
    handler_id bigint NULL,
    content nvarchar(2000) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_vis_feedback_action_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT fk_vis_feedback_action_feedback FOREIGN KEY (feedback_id) REFERENCES dbo.vis_feedback(id),
    CONSTRAINT fk_vis_feedback_action_handler FOREIGN KEY (handler_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_vis_feedback_action_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_vis_feedback_action_type CHECK (
        action_type IN ('CREATED', 'ASSIGNED', 'NOTE', 'RESOLVED', 'CLOSED', 'REOPENED')
    ),
    CONSTRAINT ck_vis_feedback_action_from CHECK (
        from_status IS NULL OR from_status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'CLOSED')
    ),
    CONSTRAINT ck_vis_feedback_action_to CHECK (to_status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX ix_vis_feedback_action_history
    ON dbo.vis_feedback_action (feedback_id, created_at, id);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('visit:feedback:view', N'查看服务反馈', 'MENU', '/api/v1/service-feedback/**', 'GET'),
    ('visit:feedback:manage', N'处理服务反馈', 'BUTTON', '/api/v1/service-feedback/**', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code IN ('HEADQUARTERS_ADMIN', 'STORE_MANAGER')
  AND permission.permission_code IN ('visit:feedback:view', 'visit:feedback:manage');

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'service-feedback', N'服务反馈', '/app/service/feedback', 'Warning', 20,
       'PC', 'visit:feedback:view'
FROM dbo.iam_menu WHERE menu_code = 'service-center';

-- 验证：一次客诉回访记录只生成一张反馈单；处理动作按状态机追加历史，不覆盖原客诉内容。
