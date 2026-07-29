-- 需求：API-AST-001~007、会员资产、移动端充值确认
-- 目的：补齐储值/积分不可变流水、充值试算和充值单，支持对账、幂等及反向交易。
-- 原则：账户余额与流水在同一事务内更新；流水只追加，不允许 UPDATE/DELETE。

CREATE TABLE dbo.ast_balance_ledger (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_balance_ledger PRIMARY KEY,
    ledger_no varchar(32) NOT NULL,
    account_id bigint NOT NULL,
    transaction_type varchar(32) NOT NULL,
    before_balance decimal(19,4) NOT NULL,
    change_amount decimal(19,4) NOT NULL,
    after_balance decimal(19,4) NOT NULL,
    source_type varchar(32) NOT NULL,
    source_id bigint NOT NULL,
    source_line_id bigint NULL,
    store_id bigint NOT NULL,
    occurred_at datetime2(3) NOT NULL CONSTRAINT df_ast_balance_ledger_time DEFAULT (sysdatetime()),
    correlation_id varchar(128) NOT NULL,
    reversed_ledger_id bigint NULL,
    note nvarchar(500) NULL,
    created_by bigint NOT NULL,
    CONSTRAINT uq_ast_balance_ledger_no UNIQUE (ledger_no),
    CONSTRAINT uq_ast_balance_ledger_correlation UNIQUE (correlation_id),
    CONSTRAINT fk_ast_balance_ledger_account FOREIGN KEY (account_id) REFERENCES dbo.ast_balance_account(id),
    CONSTRAINT fk_ast_balance_ledger_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_ast_balance_ledger_reversed FOREIGN KEY (reversed_ledger_id) REFERENCES dbo.ast_balance_ledger(id),
    CONSTRAINT fk_ast_balance_ledger_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_balance_ledger_type CHECK (transaction_type IN (
        'RECHARGE', 'RECHARGE_GIFT', 'CONSUME', 'REFUND', 'ADJUST_IN', 'ADJUST_OUT', 'REVERSAL', 'MIGRATION'
    )),
    CONSTRAINT ck_ast_balance_ledger_equation CHECK (after_balance = before_balance + change_amount),
    CONSTRAINT ck_ast_balance_ledger_nonnegative CHECK (before_balance >= 0 AND after_balance >= 0)
);

CREATE INDEX ix_ast_balance_ledger_account_time
    ON dbo.ast_balance_ledger (account_id, occurred_at DESC, id DESC);
CREATE INDEX ix_ast_balance_ledger_source
    ON dbo.ast_balance_ledger (source_type, source_id, id);

CREATE TABLE dbo.ast_point_ledger (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_point_ledger PRIMARY KEY,
    ledger_no varchar(32) NOT NULL,
    account_id bigint NOT NULL,
    transaction_type varchar(32) NOT NULL,
    before_points int NOT NULL,
    change_points int NOT NULL,
    after_points int NOT NULL,
    source_type varchar(32) NOT NULL,
    source_id bigint NOT NULL,
    expired_at datetime2(3) NULL,
    occurred_at datetime2(3) NOT NULL CONSTRAINT df_ast_point_ledger_time DEFAULT (sysdatetime()),
    correlation_id varchar(128) NOT NULL,
    reversed_ledger_id bigint NULL,
    note nvarchar(500) NULL,
    created_by bigint NOT NULL,
    CONSTRAINT uq_ast_point_ledger_no UNIQUE (ledger_no),
    CONSTRAINT uq_ast_point_ledger_correlation UNIQUE (correlation_id),
    CONSTRAINT fk_ast_point_ledger_account FOREIGN KEY (account_id) REFERENCES dbo.ast_point_account(id),
    CONSTRAINT fk_ast_point_ledger_reversed FOREIGN KEY (reversed_ledger_id) REFERENCES dbo.ast_point_ledger(id),
    CONSTRAINT fk_ast_point_ledger_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_point_ledger_type CHECK (transaction_type IN (
        'EARN', 'REDEEM', 'EXPIRE', 'REFUND', 'ADJUST_IN', 'ADJUST_OUT', 'REVERSAL', 'MIGRATION'
    )),
    CONSTRAINT ck_ast_point_ledger_equation CHECK (after_points = before_points + change_points),
    CONSTRAINT ck_ast_point_ledger_nonnegative CHECK (before_points >= 0 AND after_points >= 0)
);

CREATE INDEX ix_ast_point_ledger_account_time
    ON dbo.ast_point_ledger (account_id, occurred_at DESC, id DESC);
CREATE INDEX ix_ast_point_ledger_source
    ON dbo.ast_point_ledger (source_type, source_id, id);

CREATE TABLE dbo.ast_recharge_quote (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_recharge_quote PRIMARY KEY,
    quote_no varchar(32) NOT NULL,
    member_id bigint NOT NULL,
    account_id bigint NOT NULL,
    recharge_amount decimal(19,4) NOT NULL,
    gift_amount decimal(19,4) NOT NULL,
    credit_amount decimal(19,4) NOT NULL,
    payment_method_id bigint NOT NULL,
    expires_at datetime2(3) NOT NULL,
    used_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ast_recharge_quote_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT uq_ast_recharge_quote_no UNIQUE (quote_no),
    CONSTRAINT fk_ast_recharge_quote_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_ast_recharge_quote_account FOREIGN KEY (account_id) REFERENCES dbo.ast_balance_account(id),
    CONSTRAINT fk_ast_recharge_quote_method FOREIGN KEY (payment_method_id) REFERENCES dbo.cat_payment_method(id),
    CONSTRAINT fk_ast_recharge_quote_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_recharge_quote_amount CHECK (
        recharge_amount > 0 AND gift_amount >= 0 AND credit_amount = recharge_amount + gift_amount
    )
);

CREATE INDEX ix_ast_recharge_quote_member_time
    ON dbo.ast_recharge_quote (member_id, created_at DESC);

CREATE TABLE dbo.ast_recharge_order (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_recharge_order PRIMARY KEY,
    recharge_no varchar(32) NOT NULL,
    quote_id bigint NOT NULL,
    member_id bigint NOT NULL,
    account_id bigint NOT NULL,
    store_id bigint NOT NULL,
    recharge_amount decimal(19,4) NOT NULL,
    gift_amount decimal(19,4) NOT NULL,
    credit_amount decimal(19,4) NOT NULL,
    payment_method_id bigint NOT NULL,
    external_reference varchar(128) NULL,
    sales_employee_id bigint NULL,
    status varchar(32) NOT NULL CONSTRAINT df_ast_recharge_status DEFAULT ('PENDING_CONFIRM'),
    confirmed_by bigint NULL,
    confirmed_at datetime2(3) NULL,
    cancelled_by bigint NULL,
    cancelled_at datetime2(3) NULL,
    cancel_reason nvarchar(500) NULL,
    idempotency_key varchar(128) NOT NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ast_recharge_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_ast_recharge_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_ast_recharge_no UNIQUE (recharge_no),
    CONSTRAINT uq_ast_recharge_quote UNIQUE (quote_id),
    CONSTRAINT uq_ast_recharge_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_ast_recharge_quote FOREIGN KEY (quote_id) REFERENCES dbo.ast_recharge_quote(id),
    CONSTRAINT fk_ast_recharge_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_ast_recharge_account FOREIGN KEY (account_id) REFERENCES dbo.ast_balance_account(id),
    CONSTRAINT fk_ast_recharge_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_ast_recharge_method FOREIGN KEY (payment_method_id) REFERENCES dbo.cat_payment_method(id),
    CONSTRAINT fk_ast_recharge_sales FOREIGN KEY (sales_employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_ast_recharge_confirmer FOREIGN KEY (confirmed_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_ast_recharge_canceller FOREIGN KEY (cancelled_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_ast_recharge_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_ast_recharge_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_recharge_amount CHECK (
        recharge_amount > 0 AND gift_amount >= 0 AND credit_amount = recharge_amount + gift_amount
    ),
    CONSTRAINT ck_ast_recharge_status CHECK (status IN ('PENDING_CONFIRM', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_ast_recharge_terminal CHECK (
        (status = 'PENDING_CONFIRM' AND confirmed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND confirmed_at IS NULL)
    )
);

CREATE INDEX ix_ast_recharge_member_time
    ON dbo.ast_recharge_order (member_id, created_at DESC, status);
CREATE INDEX ix_ast_recharge_store_time
    ON dbo.ast_recharge_order (store_id, created_at DESC, status);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('member:asset:view', N'查看会员资产', 'BUTTON', '/api/v1/members/*/*', 'GET'),
    ('member:asset:manage', N'维护会员资产', 'BUTTON', '/api/v1/members/*/recharges/**', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role
CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN ('member:asset:view', 'member:asset:manage');

-- 验证：新增4张表、2项权限；所有流水均满足 before + change = after。
