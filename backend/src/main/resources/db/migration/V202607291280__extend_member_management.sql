-- 需求：API-MEM-003、API-MEM-004、API-MEM-015、API-MEM-016，会员管理-01/03/06
-- 目的：补齐会员资料编辑、冻结/解冻和手工标签维护，并保留状态变更历史。

CREATE TABLE dbo.mem_member_status_log (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mem_member_status_log PRIMARY KEY,
    member_id bigint NOT NULL,
    from_status varchar(32) NOT NULL,
    to_status varchar(32) NOT NULL,
    reason nvarchar(500) NOT NULL,
    changed_at datetime2(3) NOT NULL CONSTRAINT df_mem_member_status_changed DEFAULT (sysdatetime()),
    changed_by bigint NOT NULL,
    CONSTRAINT fk_mem_member_status_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_mem_member_status_user FOREIGN KEY (changed_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_mem_member_status_from CHECK (from_status IN ('ACTIVE', 'FROZEN', 'INACTIVE')),
    CONSTRAINT ck_mem_member_status_to CHECK (to_status IN ('ACTIVE', 'FROZEN', 'INACTIVE')),
    CONSTRAINT ck_mem_member_status_change CHECK (from_status <> to_status),
    CONSTRAINT ck_mem_member_status_reason CHECK (LEN(LTRIM(RTRIM(reason))) > 0)
);

CREATE INDEX ix_mem_member_status_history
    ON dbo.mem_member_status_log (member_id, changed_at DESC, id DESC);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('member:member:manage', N'维护和冻结会员', 'BUTTON', '/api/v1/members/**', 'PUT,POST'),
    ('member:tag:view', N'查看会员标签', 'BUTTON', '/api/v1/member-tags/**', 'GET'),
    ('member:tag:manage', N'维护会员标签', 'BUTTON', '/api/v1/members/*/tags', 'PUT');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code IN ('HEADQUARTERS_ADMIN', 'STORE_MANAGER')
  AND permission.permission_code IN ('member:member:manage', 'member:tag:view', 'member:tag:manage');

-- 标签配置本身仍沿用既有mem_tag初始数据，本迁移不加入未经甲方确认的行业标签。
