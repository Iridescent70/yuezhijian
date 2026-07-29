-- 需求：系统管理-17、系统管理-19、API-COM-008、API-VIS-005、UI-CFG-005、UI-VIS-003
-- 目的：参数化结算后回访时限，并提供可维护、可试算的满意度识别规则。

INSERT INTO dbo.sys_parameter (
    param_group, param_key, value_ciphertext, value_type, is_secret, description
)
VALUES ('VISIT', 'AFTER_SALE_DUE_HOURS', N'24', 'INTEGER', 0, N'账单结算后多少小时生成到期回访');

CREATE TABLE dbo.vis_satisfaction_rule (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_vis_satisfaction_rule PRIMARY KEY,
    rule_name nvarchar(100) NOT NULL,
    keyword_pattern nvarchar(500) NOT NULL,
    score tinyint NOT NULL,
    component_mapping_json nvarchar(max) NOT NULL CONSTRAINT df_vis_satisfaction_mapping DEFAULT (N'{}'),
    priority int NOT NULL CONSTRAINT df_vis_satisfaction_priority DEFAULT (100),
    status varchar(16) NOT NULL CONSTRAINT df_vis_satisfaction_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_vis_satisfaction_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_vis_satisfaction_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_vis_satisfaction_rule_name UNIQUE (rule_name),
    CONSTRAINT fk_vis_satisfaction_rule_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_vis_satisfaction_rule_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_vis_satisfaction_keyword CHECK (LEN(LTRIM(RTRIM(keyword_pattern))) > 0),
    CONSTRAINT ck_vis_satisfaction_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT ck_vis_satisfaction_mapping CHECK (
        ISJSON(component_mapping_json) = 1 AND LEFT(LTRIM(component_mapping_json), 1) = '{'
    ),
    CONSTRAINT ck_vis_satisfaction_priority CHECK (priority BETWEEN 0 AND 9999),
    CONSTRAINT ck_vis_satisfaction_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_vis_satisfaction_match
    ON dbo.vis_satisfaction_rule (status, priority, id);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('system:parameter:view', N'查看系统参数', 'MENU', '/api/v1/system-parameters/**', 'GET'),
    ('system:parameter:manage', N'维护系统参数', 'BUTTON', '/api/v1/system-parameters/**', 'PUT'),
    ('visit:satisfaction:view', N'查看及试算满意度规则', 'MENU', '/api/v1/satisfaction-rules/**', 'GET,POST'),
    ('visit:satisfaction:manage', N'维护满意度规则', 'BUTTON', '/api/v1/satisfaction-rules/**', 'POST,PUT');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN (
      'system:parameter:view', 'system:parameter:manage',
      'visit:satisfaction:view', 'visit:satisfaction:manage'
  );

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'STORE_MANAGER'
  AND permission.permission_code = 'visit:satisfaction:view';

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'satisfaction-rules', N'满意度规则', '/app/service/satisfaction-rules', 'Star', 30,
       'PC', 'visit:satisfaction:view'
FROM dbo.iam_menu WHERE menu_code = 'service-center';

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'system-parameters', N'系统参数', '/app/system/parameters', 'Operation', 50,
       'PC', 'system:parameter:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

-- 不预置满意度关键词，规则须按甲方确认口径录入；样例试算不写业务数据。
