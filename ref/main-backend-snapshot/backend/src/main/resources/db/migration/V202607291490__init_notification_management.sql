CREATE TABLE dbo.ntf_message (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ntf_message PRIMARY KEY,
    notification_no varchar(40) NOT NULL,
    message_type varchar(32) NOT NULL,
    event_code varchar(64) NOT NULL,
    title nvarchar(100) NOT NULL,
    body nvarchar(4000) NOT NULL,
    scope_type varchar(16) NOT NULL,
    business_type varchar(64) NULL,
    business_id bigint NULL,
    route varchar(255) NULL,
    valid_from datetime2(3) NULL,
    valid_to datetime2(3) NULL,
    priority int NOT NULL CONSTRAINT df_ntf_message_priority DEFAULT (0),
    pinned bit NOT NULL CONSTRAINT df_ntf_message_pinned DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_ntf_message_status DEFAULT ('DRAFT'),
    published_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ntf_message_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_ntf_message_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_ntf_message_no UNIQUE (notification_no),
    CONSTRAINT fk_ntf_message_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_ntf_message_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ntf_message_type CHECK (message_type IN (
        'ANNOUNCEMENT', 'APPOINTMENT', 'CARD_EXPIRY', 'BIRTHDAY', 'BALANCE_LOW',
        'CONSUMPTION', 'SYSTEM', 'DAILY_REPORT', 'BILL_ALERT', 'RECONCILIATION',
        'BILL_REVERSAL', 'BALANCE_REVERSAL', 'CARD_REVERSAL'
    )),
    CONSTRAINT ck_ntf_message_scope CHECK (scope_type IN ('ALL', 'STORES')),
    CONSTRAINT ck_ntf_message_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT ck_ntf_message_priority CHECK (priority BETWEEN 0 AND 9999),
    CONSTRAINT ck_ntf_message_title CHECK (LEN(LTRIM(RTRIM(title))) > 0),
    CONSTRAINT ck_ntf_message_body CHECK (LEN(LTRIM(RTRIM(body))) > 0),
    CONSTRAINT ck_ntf_message_validity CHECK (
        valid_from IS NULL OR valid_to IS NULL OR valid_to > valid_from
    ),
    CONSTRAINT ck_ntf_message_business CHECK (
        (business_type IS NULL AND business_id IS NULL)
        OR (business_type IS NOT NULL AND business_id IS NOT NULL)
    ),
    CONSTRAINT ck_ntf_message_publish CHECK (
        status <> 'PUBLISHED' OR published_at IS NOT NULL
    ),
    CONSTRAINT ck_ntf_message_route CHECK (route IS NULL OR (route LIKE '/%' AND route NOT LIKE '//%'))
);

CREATE UNIQUE INDEX uq_ntf_message_business_event
    ON dbo.ntf_message (event_code, business_type, business_id)
    WHERE business_type IS NOT NULL AND business_id IS NOT NULL;

CREATE INDEX ix_ntf_message_feed
    ON dbo.ntf_message (status, published_at DESC, message_type, id DESC)
    INCLUDE (scope_type, valid_from, valid_to, priority, pinned, title);

CREATE INDEX ix_ntf_message_management
    ON dbo.ntf_message (message_type, updated_at DESC, id DESC)
    INCLUDE (status, title, scope_type);

CREATE TABLE dbo.ntf_message_store (
    message_id bigint NOT NULL,
    store_id bigint NOT NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ntf_message_store_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT pk_ntf_message_store PRIMARY KEY (message_id, store_id),
    CONSTRAINT fk_ntf_message_store_message FOREIGN KEY (message_id) REFERENCES dbo.ntf_message(id),
    CONSTRAINT fk_ntf_message_store_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_ntf_message_store_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id)
);

CREATE INDEX ix_ntf_message_store_target
    ON dbo.ntf_message_store (store_id, message_id);

CREATE TABLE dbo.ntf_message_read (
    message_id bigint NOT NULL,
    user_id bigint NOT NULL,
    read_at datetime2(3) NOT NULL CONSTRAINT df_ntf_message_read_at DEFAULT (sysdatetime()),
    CONSTRAINT pk_ntf_message_read PRIMARY KEY (message_id, user_id),
    CONSTRAINT fk_ntf_message_read_message FOREIGN KEY (message_id) REFERENCES dbo.ntf_message(id),
    CONSTRAINT fk_ntf_message_read_user FOREIGN KEY (user_id) REFERENCES dbo.iam_user(id)
);

CREATE INDEX ix_ntf_message_read_user
    ON dbo.ntf_message_read (user_id, read_at DESC, message_id);

CREATE TABLE dbo.ntf_template (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ntf_template PRIMARY KEY,
    event_code varchar(64) NOT NULL,
    event_name nvarchar(100) NOT NULL,
    channel varchar(16) NOT NULL CONSTRAINT df_ntf_template_channel DEFAULT ('IN_APP'),
    title_template nvarchar(100) NOT NULL,
    body_template nvarchar(4000) NOT NULL,
    variables_csv varchar(1000) NOT NULL CONSTRAINT df_ntf_template_variables DEFAULT (''),
    status varchar(32) NOT NULL CONSTRAINT df_ntf_template_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_ntf_template_created DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_ntf_template_updated DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_ntf_template_event UNIQUE (event_code),
    CONSTRAINT fk_ntf_template_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_ntf_template_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ntf_template_event CHECK (
        LEN(event_code) BETWEEN 2 AND 64 AND event_code NOT LIKE '%[^A-Z0-9_]%'
    ),
    CONSTRAINT ck_ntf_template_channel CHECK (channel = 'IN_APP'),
    CONSTRAINT ck_ntf_template_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_ntf_template_name CHECK (LEN(LTRIM(RTRIM(event_name))) > 0),
    CONSTRAINT ck_ntf_template_title CHECK (LEN(LTRIM(RTRIM(title_template))) > 0),
    CONSTRAINT ck_ntf_template_body CHECK (LEN(LTRIM(RTRIM(body_template))) > 0)
);

INSERT INTO dbo.ntf_template (
    event_code, event_name, title_template, body_template, variables_csv
)
VALUES
    ('BILL_REVERSAL', N'账单冲销通知', N'账单{{billNo}}已冲销',
     N'{{storeName}}账单{{billNo}}已完成冲销，冲销单{{reversalNo}}，退款{{refundAmount}}元。原因：{{reason}}',
     'billNo,reversalNo,storeName,refundAmount,reason'),
    ('BALANCE_REVERSAL', N'储值冲销通知', N'储值{{rechargeNo}}已冲销',
     N'会员{{memberName}}的储值{{rechargeNo}}已冲销，金额{{amount}}元。原因：{{reason}}',
     'rechargeNo,memberName,amount,storeName,reason'),
    ('CARD_REVERSAL', N'次卡冲销通知', N'次卡{{cardNo}}已冲销',
     N'会员{{memberName}}的次卡{{cardNo}}已冲销{{quantity}}次。原因：{{reason}}',
     'cardNo,memberName,itemName,quantity,storeName,reason');

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('notification:view', N'查看站内消息', 'MENU', '/api/v1/notifications/**', 'GET,POST'),
    ('system:announcement:view', N'查看通知公告', 'MENU', '/api/v1/announcements/**', 'GET'),
    ('system:announcement:manage', N'维护通知公告', 'BUTTON', '/api/v1/announcements/**', 'POST,PUT'),
    ('system:notification-template:view', N'查看通知模板', 'MENU', '/api/v1/notification-templates/**', 'GET'),
    ('system:notification-template:manage', N'维护通知模板', 'BUTTON', '/api/v1/notification-templates/**', 'POST,PUT');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN (
      'notification:view', 'system:announcement:view', 'system:announcement:manage',
      'system:notification-template:view', 'system:notification-template:manage'
  );

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'STORE_MANAGER' AND permission.permission_code = 'notification:view';

UPDATE dbo.iam_menu SET sort_no = 85
WHERE menu_code = 'audit-logs' AND parent_id = (
    SELECT id FROM dbo.iam_menu WHERE menu_code = 'system'
);

INSERT INTO dbo.iam_menu (
    parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code
)
VALUES (NULL, 'notifications', N'消息中心', '/app/notifications', 'Bell', 70, 'PC', 'notification:view');

INSERT INTO dbo.iam_menu (
    parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code
)
SELECT id, 'announcements', N'通知公告', '/app/system/announcements', 'Notification', 72,
       'PC', 'system:announcement:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

INSERT INTO dbo.iam_menu (
    parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code
)
SELECT id, 'notification-templates', N'通知模板', '/app/system/notification-templates', 'ChatLineSquare', 74,
       'PC', 'system:notification-template:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

-- 验证：消息、门店范围、阅读记录、模板4张表；5项权限和3个菜单。
