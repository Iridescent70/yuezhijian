-- 需求：系统管理-16、快捷入口-08、结算管理-02
-- 目的：建立代金券模板、不可变券码快照、会员绑定、结算核销和冲销返券闭环。

CREATE TABLE dbo.cat_voucher (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_voucher PRIMARY KEY,
    voucher_code varchar(64) NOT NULL,
    voucher_name nvarchar(200) NOT NULL,
    benefit_type varchar(32) NOT NULL,
    face_amount decimal(19,4) NOT NULL,
    discount_rate decimal(9,6) NOT NULL,
    min_spend decimal(19,4) NOT NULL CONSTRAINT df_cat_voucher_min_spend DEFAULT (0),
    valid_days int NOT NULL,
    commission_rule nvarchar(1000) NULL,
    status varchar(16) NOT NULL CONSTRAINT df_cat_voucher_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_voucher_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_voucher_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_cat_voucher_code UNIQUE (voucher_code),
    CONSTRAINT fk_cat_voucher_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_cat_voucher_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_cat_voucher_type CHECK (benefit_type IN ('FIXED_AMOUNT', 'DISCOUNT')),
    CONSTRAINT ck_cat_voucher_value CHECK (
        (benefit_type = 'FIXED_AMOUNT' AND face_amount > 0 AND discount_rate = 1)
        OR (benefit_type = 'DISCOUNT' AND face_amount = 0 AND discount_rate > 0 AND discount_rate < 1)
    ),
    CONSTRAINT ck_cat_voucher_min_spend CHECK (min_spend >= 0),
    CONSTRAINT ck_cat_voucher_valid_days CHECK (valid_days BETWEEN 1 AND 3650),
    CONSTRAINT ck_cat_voucher_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE dbo.ben_voucher_issue_batch (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ben_voucher_issue PRIMARY KEY,
    batch_no varchar(32) NOT NULL,
    voucher_id bigint NOT NULL,
    member_id bigint NULL,
    issue_count int NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ben_voucher_issue_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT uq_ben_voucher_issue_no UNIQUE (batch_no),
    CONSTRAINT uq_ben_voucher_issue_key UNIQUE (idempotency_key),
    CONSTRAINT fk_ben_voucher_issue_voucher FOREIGN KEY (voucher_id) REFERENCES dbo.cat_voucher(id),
    CONSTRAINT fk_ben_voucher_issue_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_ben_voucher_issue_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ben_voucher_issue_count CHECK (issue_count BETWEEN 1 AND 100)
);

CREATE TABLE dbo.ben_voucher_code (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ben_voucher_code PRIMARY KEY,
    code varchar(64) NOT NULL,
    issue_batch_id bigint NOT NULL,
    voucher_id bigint NOT NULL,
    voucher_code varchar(64) NOT NULL,
    voucher_name nvarchar(200) NOT NULL,
    benefit_type varchar(32) NOT NULL,
    face_amount decimal(19,4) NOT NULL,
    discount_rate decimal(9,6) NOT NULL,
    min_spend decimal(19,4) NOT NULL,
    member_id bigint NULL,
    valid_from datetime2(3) NOT NULL,
    valid_until datetime2(3) NOT NULL,
    status varchar(16) NOT NULL,
    bound_at datetime2(3) NULL,
    redeemed_at datetime2(3) NULL,
    redeemed_bill_id bigint NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ben_voucher_code_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_ben_voucher_code_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_ben_voucher_code UNIQUE (code),
    CONSTRAINT fk_ben_voucher_code_batch FOREIGN KEY (issue_batch_id) REFERENCES dbo.ben_voucher_issue_batch(id),
    CONSTRAINT fk_ben_voucher_code_voucher FOREIGN KEY (voucher_id) REFERENCES dbo.cat_voucher(id),
    CONSTRAINT fk_ben_voucher_code_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_ben_voucher_code_bill FOREIGN KEY (redeemed_bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT fk_ben_voucher_code_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_ben_voucher_code_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ben_voucher_code_type CHECK (benefit_type IN ('FIXED_AMOUNT', 'DISCOUNT')),
    CONSTRAINT ck_ben_voucher_code_valid CHECK (valid_until > valid_from),
    CONSTRAINT ck_ben_voucher_code_status CHECK (status IN ('UNBOUND', 'BOUND', 'REDEEMED', 'EXPIRED', 'VOIDED')),
    CONSTRAINT ck_ben_voucher_code_owner CHECK (
        (status = 'UNBOUND' AND member_id IS NULL AND bound_at IS NULL)
        OR (status IN ('BOUND', 'REDEEMED', 'EXPIRED', 'VOIDED') AND member_id IS NOT NULL)
    ),
    CONSTRAINT ck_ben_voucher_code_redeemed CHECK (
        (status = 'REDEEMED' AND redeemed_at IS NOT NULL AND redeemed_bill_id IS NOT NULL)
        OR (status <> 'REDEEMED' AND redeemed_at IS NULL AND redeemed_bill_id IS NULL)
    )
);

CREATE INDEX ix_ben_voucher_code_member_status
    ON dbo.ben_voucher_code (member_id, status, valid_until, id);

CREATE TABLE dbo.ben_voucher_ledger (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ben_voucher_ledger PRIMARY KEY,
    ledger_no varchar(32) NOT NULL,
    code_id bigint NOT NULL,
    ledger_type varchar(16) NOT NULL,
    member_id bigint NOT NULL,
    amount decimal(19,4) NOT NULL,
    source_bill_id bigint NULL,
    source_reversal_id bigint NULL,
    reversed_ledger_id bigint NULL,
    idempotency_key varchar(128) NULL,
    note nvarchar(500) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ben_voucher_ledger_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT uq_ben_voucher_ledger_no UNIQUE (ledger_no),
    CONSTRAINT uq_ben_voucher_ledger_key UNIQUE (idempotency_key),
    CONSTRAINT fk_ben_voucher_ledger_code FOREIGN KEY (code_id) REFERENCES dbo.ben_voucher_code(id),
    CONSTRAINT fk_ben_voucher_ledger_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_ben_voucher_ledger_bill FOREIGN KEY (source_bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT fk_ben_voucher_ledger_reversal FOREIGN KEY (source_reversal_id) REFERENCES dbo.trd_reversal(id),
    CONSTRAINT fk_ben_voucher_ledger_reversed FOREIGN KEY (reversed_ledger_id) REFERENCES dbo.ben_voucher_ledger(id),
    CONSTRAINT fk_ben_voucher_ledger_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ben_voucher_ledger_type CHECK (ledger_type IN ('BIND', 'REDEEM', 'RETURN')),
    CONSTRAINT ck_ben_voucher_ledger_amount CHECK (amount >= 0)
);

CREATE UNIQUE INDEX ux_ben_voucher_ledger_reversed
    ON dbo.ben_voucher_ledger (reversed_ledger_id) WHERE reversed_ledger_id IS NOT NULL;

ALTER TABLE dbo.trd_settlement_quote_asset ADD voucher_code_id bigint NULL;
GO

ALTER TABLE dbo.trd_settlement_quote_asset ADD CONSTRAINT fk_trd_quote_asset_voucher
    FOREIGN KEY (voucher_code_id) REFERENCES dbo.ben_voucher_code(id);
ALTER TABLE dbo.trd_settlement_quote_asset DROP CONSTRAINT ck_trd_quote_asset_type;
ALTER TABLE dbo.trd_settlement_quote_asset DROP CONSTRAINT ck_trd_quote_asset_reference;
ALTER TABLE dbo.trd_settlement_quote_asset ADD CONSTRAINT ck_trd_quote_asset_type
    CHECK (asset_type IN ('BALANCE', 'POINT', 'CARD', 'VOUCHER'));
ALTER TABLE dbo.trd_settlement_quote_asset ADD CONSTRAINT ck_trd_quote_asset_reference CHECK (
    (asset_type = 'CARD' AND member_card_id IS NOT NULL AND member_card_balance_id IS NOT NULL
        AND bill_line_id IS NOT NULL AND service_id IS NOT NULL AND voucher_code_id IS NULL)
    OR (asset_type IN ('BALANCE', 'POINT') AND member_card_id IS NULL
        AND member_card_balance_id IS NULL AND bill_line_id IS NULL AND voucher_code_id IS NULL)
    OR (asset_type = 'VOUCHER' AND voucher_code_id IS NOT NULL AND member_card_id IS NULL
        AND member_card_balance_id IS NULL AND bill_line_id IS NULL AND service_id IS NULL)
);

ALTER TABLE dbo.trd_bill_asset_usage ADD voucher_code_id bigint NULL;
GO

ALTER TABLE dbo.trd_bill_asset_usage ADD CONSTRAINT fk_trd_asset_usage_voucher
    FOREIGN KEY (voucher_code_id) REFERENCES dbo.ben_voucher_code(id);
ALTER TABLE dbo.trd_bill_asset_usage DROP CONSTRAINT ck_trd_asset_usage_type;
ALTER TABLE dbo.trd_bill_asset_usage ADD CONSTRAINT ck_trd_asset_usage_type
    CHECK (asset_type IN ('BALANCE', 'POINT', 'CARD', 'VOUCHER'));

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('benefit:voucher:view', N'查看代金券', 'MENU', '/api/v1/voucher*/**', 'GET'),
    ('benefit:voucher:manage', N'维护代金券定义', 'BUTTON', '/api/v1/vouchers/**', 'POST'),
    ('benefit:voucher:issue', N'发放和绑定代金券', 'BUTTON', '/api/v1/voucher*/**', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN ('benefit:voucher:view', 'benefit:voucher:manage', 'benefit:voucher:issue');

INSERT INTO dbo.iam_menu (menu_code, name, route, icon, sort_no, client_type, permission_code)
VALUES ('vouchers', N'代金券管理', '/app/benefits/vouchers', 'Ticket', 58, 'PC', 'benefit:voucher:view');

-- 验证：模板修改不改变已发券快照；券只能绑定一次、核销一次；整单冲销后恢复为可用。
