-- 需求：会员管理-03、结算管理-05、API-VIS-001~004、UI-VIS-001
-- 目的：结算后自动生成回访任务，支持多技师分别登记、满意度、客诉及继续跟进。

CREATE TABLE dbo.vis_visit_task (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_vis_visit_task PRIMARY KEY,
    task_no varchar(32) NOT NULL,
    member_id bigint NOT NULL,
    bill_id bigint NOT NULL,
    store_id bigint NOT NULL,
    due_at datetime2(3) NOT NULL,
    task_type varchar(32) NOT NULL CONSTRAINT df_vis_task_type DEFAULT ('AFTER_SALE'),
    status varchar(16) NOT NULL CONSTRAINT df_vis_task_status DEFAULT ('PENDING'),
    complaint_flag bit NOT NULL CONSTRAINT df_vis_task_complaint DEFAULT (0),
    conclusion nvarchar(1000) NULL,
    completed_at datetime2(3) NULL,
    canceled_at datetime2(3) NULL,
    cancel_reason nvarchar(500) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_vis_task_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_vis_task_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_vis_visit_task_no UNIQUE (task_no),
    CONSTRAINT uq_vis_visit_task_bill UNIQUE (bill_id),
    CONSTRAINT fk_vis_task_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_vis_task_bill FOREIGN KEY (bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT fk_vis_task_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_vis_task_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_vis_task_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_vis_task_type CHECK (task_type IN ('AFTER_SALE')),
    CONSTRAINT ck_vis_task_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_vis_task_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status = 'CANCELLED' AND canceled_at IS NOT NULL AND cancel_reason IS NOT NULL)
        OR status = 'PENDING'
    )
);

CREATE INDEX ix_vis_task_store_due
    ON dbo.vis_visit_task (store_id, status, due_at, id);
CREATE INDEX ix_vis_task_member
    ON dbo.vis_visit_task (member_id, created_at DESC);
CREATE INDEX ix_vis_task_complaint
    ON dbo.vis_visit_task (store_id, complaint_flag, created_at DESC)
    WHERE complaint_flag = 1;

CREATE TABLE dbo.vis_visit_participant (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_vis_visit_participant PRIMARY KEY,
    task_id bigint NOT NULL,
    employee_id bigint NULL,
    employee_name_snapshot nvarchar(100) NOT NULL,
    service_summary nvarchar(1000) NOT NULL,
    status varchar(16) NOT NULL CONSTRAINT df_vis_participant_status DEFAULT ('PENDING'),
    completed_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_vis_participant_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT fk_vis_participant_task FOREIGN KEY (task_id) REFERENCES dbo.vis_visit_task(id),
    CONSTRAINT fk_vis_participant_employee FOREIGN KEY (employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_vis_participant_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_vis_participant_status CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT ck_vis_participant_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL) OR status = 'PENDING'
    )
);

CREATE UNIQUE INDEX ux_vis_participant_employee
    ON dbo.vis_visit_participant (task_id, employee_id)
    WHERE employee_id IS NOT NULL;
CREATE INDEX ix_vis_participant_employee_status
    ON dbo.vis_visit_participant (employee_id, status, task_id)
    WHERE employee_id IS NOT NULL;

CREATE TABLE dbo.vis_visit_record (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_vis_visit_record PRIMARY KEY,
    task_id bigint NOT NULL,
    participant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    result_code varchar(24) NOT NULL,
    satisfaction_score tinyint NULL,
    complaint_flag bit NOT NULL CONSTRAINT df_vis_record_complaint DEFAULT (0),
    content nvarchar(2000) NULL,
    next_follow_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_vis_record_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT fk_vis_record_task FOREIGN KEY (task_id) REFERENCES dbo.vis_visit_task(id),
    CONSTRAINT fk_vis_record_participant FOREIGN KEY (participant_id) REFERENCES dbo.vis_visit_participant(id),
    CONSTRAINT fk_vis_record_employee FOREIGN KEY (employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_vis_record_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_vis_record_result CHECK (result_code IN ('CONTACTED', 'NO_ANSWER', 'DECLINED', 'FOLLOW_UP')),
    CONSTRAINT ck_vis_record_score CHECK (satisfaction_score IS NULL OR satisfaction_score BETWEEN 1 AND 5),
    CONSTRAINT ck_vis_record_contact_score CHECK (
        (result_code = 'CONTACTED' AND satisfaction_score IS NOT NULL)
        OR (result_code <> 'CONTACTED' AND satisfaction_score IS NULL)
    ),
    CONSTRAINT ck_vis_record_follow_time CHECK (
        (result_code IN ('NO_ANSWER', 'FOLLOW_UP') AND next_follow_at IS NOT NULL)
        OR (result_code IN ('CONTACTED', 'DECLINED'))
    ),
    CONSTRAINT ck_vis_record_complaint_content CHECK (complaint_flag = 0 OR content IS NOT NULL)
);

CREATE INDEX ix_vis_record_task_created
    ON dbo.vis_visit_record (task_id, created_at, id);
CREATE INDEX ix_vis_record_next_follow
    ON dbo.vis_visit_record (next_follow_at, task_id)
    WHERE next_follow_at IS NOT NULL;

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('visit:task:view', N'查看回访任务', 'MENU', '/api/v1/visit-tasks/**', 'GET'),
    ('visit:task:manage', N'登记和完成回访', 'BUTTON', '/api/v1/visit-tasks/**', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code IN ('HEADQUARTERS_ADMIN', 'STORE_MANAGER')
  AND permission.permission_code IN ('visit:task:view', 'visit:task:manage');

INSERT INTO dbo.iam_menu (menu_code, name, route, icon, sort_no, client_type, permission_code)
VALUES ('service-center', N'客户服务', '/app/service', 'Service', 65, 'PC', NULL);

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'visit-tasks', N'回访管理', '/app/service/visits', 'ChatLineSquare', 10,
       'PC', 'visit:task:view'
FROM dbo.iam_menu WHERE menu_code = 'service-center';

-- 验证：会员账单重复结算只产生一张任务；每位服务技师一条参与项；整单冲销取消未完成任务。
