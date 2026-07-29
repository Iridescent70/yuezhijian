-- 需求：API-MEM-011、API-MEM-012、UI-MEM-009、优化会员管理-05
-- 目的：会员归属调整必须经过申请、审批和按生效日执行，不直接覆盖历史业务归属。

CREATE TABLE dbo.mem_ownership_adjustment (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mem_ownership_adjustment PRIMARY KEY,
    adjustment_no varchar(32) NOT NULL,
    member_id bigint NOT NULL,
    old_store_id bigint NOT NULL,
    new_store_id bigint NOT NULL,
    effective_date date NOT NULL,
    share_rule_json nvarchar(max) NOT NULL CONSTRAINT df_mem_ownership_share DEFAULT (N'{}'),
    reason nvarchar(500) NOT NULL,
    approval_status varchar(16) NOT NULL CONSTRAINT df_mem_ownership_approval DEFAULT ('PENDING'),
    execution_status varchar(16) NOT NULL CONSTRAINT df_mem_ownership_execution DEFAULT ('WAITING'),
    requested_by bigint NOT NULL,
    requested_at datetime2(3) NOT NULL CONSTRAINT df_mem_ownership_requested DEFAULT (sysdatetime()),
    reviewed_by bigint NULL,
    reviewed_at datetime2(3) NULL,
    review_comment nvarchar(500) NULL,
    applied_at datetime2(3) NULL,
    execution_message nvarchar(500) NULL,
    active_member_key AS (
        CASE WHEN execution_status IN ('WAITING', 'PROCESSING') THEN member_id ELSE -id END
    ) PERSISTED,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_mem_ownership_adjustment_no UNIQUE (adjustment_no),
    CONSTRAINT fk_mem_ownership_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_mem_ownership_old_store FOREIGN KEY (old_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_mem_ownership_new_store FOREIGN KEY (new_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_mem_ownership_requester FOREIGN KEY (requested_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_mem_ownership_reviewer FOREIGN KEY (reviewed_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_mem_ownership_store CHECK (old_store_id <> new_store_id),
    CONSTRAINT ck_mem_ownership_share CHECK (
        ISJSON(share_rule_json) = 1 AND LEFT(LTRIM(share_rule_json), 1) = '{'
    ),
    CONSTRAINT ck_mem_ownership_reason CHECK (LEN(LTRIM(RTRIM(reason))) > 0),
    CONSTRAINT ck_mem_ownership_approval CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_mem_ownership_execution CHECK (
        execution_status IN ('WAITING', 'PROCESSING', 'APPLIED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_mem_ownership_state CHECK (
        (approval_status = 'PENDING' AND execution_status = 'WAITING')
        OR (approval_status = 'REJECTED' AND execution_status = 'CANCELLED')
        OR (approval_status = 'APPROVED' AND execution_status IN ('WAITING', 'PROCESSING', 'APPLIED', 'FAILED'))
    ),
    CONSTRAINT ck_mem_ownership_review CHECK (
        (approval_status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (approval_status <> 'PENDING' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_mem_ownership_applied CHECK (
        (execution_status = 'APPLIED' AND applied_at IS NOT NULL)
        OR (execution_status <> 'APPLIED' AND applied_at IS NULL)
    )
);

CREATE UNIQUE INDEX ux_mem_ownership_active_member
    ON dbo.mem_ownership_adjustment (active_member_key);

CREATE INDEX ix_mem_ownership_queue
    ON dbo.mem_ownership_adjustment (approval_status, execution_status, effective_date, id);

CREATE INDEX ix_mem_ownership_member_history
    ON dbo.mem_ownership_adjustment (member_id, requested_at DESC, id DESC);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('member:ownership:view', N'查看会员归属调整', 'MENU', '/api/v1/ownership-adjustments/**', 'GET'),
    ('member:ownership:manage', N'申请会员归属调整', 'BUTTON', '/api/v1/members/*/ownership-adjustments', 'POST'),
    ('member:ownership:approve', N'审批会员归属调整', 'BUTTON', '/api/v1/ownership-adjustments/*/**', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN (
      'member:ownership:view', 'member:ownership:manage', 'member:ownership:approve'
  );

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'STORE_MANAGER'
  AND permission.permission_code IN ('member:ownership:view', 'member:ownership:manage');

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'member-list', N'会员列表', '/app/members', 'List', 10, 'PC', 'member:member:view'
FROM dbo.iam_menu WHERE menu_code = 'member';

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'member-ownership', N'归属调整', '/app/members/ownership', 'Switch', 20,
       'PC', 'member:ownership:view'
FROM dbo.iam_menu WHERE menu_code = 'member';

-- share_rule_json仅保存本次申请的甲方确认规则快照；分润计算由后续独立分润模块执行。
