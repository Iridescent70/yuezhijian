-- 需求：薪酬-01、结算管理-04、次卡管理-01、统计分析-04
-- 目的：建立版本化提成方案、结算事实流水及整单冲销的负向追溯链路。

CREATE TABLE dbo.comm_plan (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_comm_plan PRIMARY KEY,
    plan_code varchar(64) NOT NULL,
    plan_name nvarchar(200) NOT NULL,
    scene varchar(32) NOT NULL,
    calculation_mode varchar(16) NOT NULL,
    rate decimal(9,6) NULL,
    fixed_amount decimal(19,4) NULL,
    store_id bigint NULL,
    position_id bigint NULL,
    effective_from date NOT NULL,
    effective_to date NULL,
    status varchar(16) NOT NULL CONSTRAINT df_comm_plan_status DEFAULT ('ACTIVE'),
    rule_version int NOT NULL CONSTRAINT df_comm_plan_rule_version DEFAULT (1),
    created_at datetime2(3) NOT NULL CONSTRAINT df_comm_plan_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_comm_plan_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_comm_plan_code UNIQUE (plan_code),
    CONSTRAINT fk_comm_plan_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_comm_plan_position FOREIGN KEY (position_id) REFERENCES dbo.org_position(id),
    CONSTRAINT fk_comm_plan_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_comm_plan_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_comm_plan_scene CHECK (scene IN ('SERVICE', 'CARD_SALE', 'CARD_CONSUME')),
    CONSTRAINT ck_comm_plan_mode CHECK (calculation_mode IN ('RATE', 'FIXED', 'NONE')),
    CONSTRAINT ck_comm_plan_values CHECK (
        (calculation_mode = 'RATE' AND rate BETWEEN 0 AND 1 AND fixed_amount IS NULL)
        OR (calculation_mode = 'FIXED' AND fixed_amount >= 0 AND rate IS NULL)
        OR (calculation_mode = 'NONE' AND rate IS NULL AND fixed_amount IS NULL)
    ),
    CONSTRAINT ck_comm_plan_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_comm_plan_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_comm_plan_version CHECK (rule_version > 0)
);

CREATE INDEX ix_comm_plan_match
    ON dbo.comm_plan (scene, status, store_id, position_id, effective_from, effective_to);

CREATE TABLE dbo.comm_plan_revision (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_comm_plan_revision PRIMARY KEY,
    plan_id bigint NOT NULL,
    rule_version int NOT NULL,
    plan_name nvarchar(200) NOT NULL,
    scene varchar(32) NOT NULL,
    calculation_mode varchar(16) NOT NULL,
    rate decimal(9,6) NULL,
    fixed_amount decimal(19,4) NULL,
    store_id bigint NULL,
    position_id bigint NULL,
    effective_from date NOT NULL,
    effective_to date NULL,
    status varchar(16) NOT NULL,
    recorded_at datetime2(3) NOT NULL CONSTRAINT df_comm_plan_revision_recorded DEFAULT (sysdatetime()),
    recorded_by bigint NOT NULL,
    CONSTRAINT uq_comm_plan_revision UNIQUE (plan_id, rule_version),
    CONSTRAINT fk_comm_plan_revision_plan FOREIGN KEY (plan_id) REFERENCES dbo.comm_plan(id),
    CONSTRAINT fk_comm_plan_revision_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_comm_plan_revision_position FOREIGN KEY (position_id) REFERENCES dbo.org_position(id),
    CONSTRAINT fk_comm_plan_revision_recorder FOREIGN KEY (recorded_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_comm_plan_revision_version CHECK (rule_version > 0)
);

CREATE TABLE dbo.comm_ledger (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_comm_ledger PRIMARY KEY,
    ledger_no varchar(32) NOT NULL,
    employee_id bigint NOT NULL,
    store_id bigint NOT NULL,
    commission_type varchar(32) NOT NULL,
    source_type varchar(32) NOT NULL,
    source_id bigint NOT NULL,
    source_no varchar(32) NOT NULL,
    source_line_id bigint NULL,
    source_line_name nvarchar(200) NULL,
    base_amount decimal(19,4) NOT NULL,
    rate decimal(9,6) NULL,
    commission_amount decimal(19,4) NOT NULL,
    calculation_status varchar(24) NOT NULL,
    plan_id bigint NULL,
    plan_name nvarchar(200) NULL,
    plan_rule_version int NULL,
    formula_snapshot nvarchar(2000) NOT NULL,
    occurred_at datetime2(3) NOT NULL,
    correlation_id varchar(128) NOT NULL,
    reversed_ledger_id bigint NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_comm_ledger_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT uq_comm_ledger_no UNIQUE (ledger_no),
    CONSTRAINT uq_comm_ledger_correlation UNIQUE (correlation_id),
    CONSTRAINT fk_comm_ledger_employee FOREIGN KEY (employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_comm_ledger_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_comm_ledger_plan FOREIGN KEY (plan_id) REFERENCES dbo.comm_plan(id),
    CONSTRAINT fk_comm_ledger_reversed FOREIGN KEY (reversed_ledger_id) REFERENCES dbo.comm_ledger(id),
    CONSTRAINT fk_comm_ledger_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_comm_ledger_type CHECK (commission_type IN ('SERVICE', 'CARD_SALE', 'CARD_CONSUME')),
    CONSTRAINT ck_comm_ledger_source CHECK (source_type IN ('BILL', 'BILL_REVERSAL', 'CARD_SALE', 'CARD_REFUND', 'CARD_EXCHANGE')),
    CONSTRAINT ck_comm_ledger_status CHECK (calculation_status IN ('CALCULATED', 'PENDING_RULE')),
    CONSTRAINT ck_comm_ledger_plan_snapshot CHECK (
        (calculation_status = 'CALCULATED' AND plan_id IS NOT NULL AND plan_name IS NOT NULL AND plan_rule_version IS NOT NULL)
        OR (calculation_status = 'PENDING_RULE' AND plan_id IS NULL AND plan_name IS NULL AND plan_rule_version IS NULL)
    ),
    CONSTRAINT ck_comm_ledger_direction CHECK (
        (reversed_ledger_id IS NULL AND base_amount >= 0 AND commission_amount >= 0)
        OR (reversed_ledger_id IS NOT NULL AND base_amount <= 0 AND commission_amount <= 0)
    )
);

CREATE INDEX ix_comm_ledger_employee_date
    ON dbo.comm_ledger (employee_id, occurred_at DESC, id DESC);
CREATE INDEX ix_comm_ledger_store_date
    ON dbo.comm_ledger (store_id, occurred_at DESC, id DESC);
CREATE INDEX ix_comm_ledger_source
    ON dbo.comm_ledger (source_type, source_id, source_line_id);
CREATE UNIQUE INDEX ux_comm_ledger_reversed
    ON dbo.comm_ledger (reversed_ledger_id)
    WHERE reversed_ledger_id IS NOT NULL;

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('commission:plan:view', N'查看提成方案', 'MENU', '/api/v1/commission-plans/**', 'GET'),
    ('commission:plan:manage', N'维护提成方案', 'BUTTON', '/api/v1/commission-plans/**', 'POST'),
    ('commission:ledger:view', N'查看提成流水', 'MENU', '/api/v1/commission-ledgers/**', 'GET');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN (
      'commission:plan:view', 'commission:plan:manage', 'commission:ledger:view'
  );

INSERT INTO dbo.iam_menu (menu_code, name, route, icon, sort_no, client_type, permission_code)
VALUES ('commission', N'薪酬提成', '/app/commission', 'Money', 60, 'PC', NULL);

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'commission-plans', N'提成方案', '/app/commission/plans', 'SetUp', 10, 'PC', 'commission:plan:view'
FROM dbo.iam_menu WHERE menu_code = 'commission';

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'commission-ledgers', N'提成流水', '/app/commission/ledgers', 'List', 20, 'PC', 'commission:ledger:view'
FROM dbo.iam_menu WHERE menu_code = 'commission';

-- 验证：结算同一账单不会重复计提；方案修改后历史流水快照不变；整单冲销生成唯一负向流水。
