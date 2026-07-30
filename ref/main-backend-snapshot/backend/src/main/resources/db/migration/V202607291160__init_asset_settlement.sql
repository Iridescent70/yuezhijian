-- 需求：API-TRD-008~010、结算管理-01/02
-- 目的：将储值、积分和次卡接入结算试算，保存资产版本快照并在结算时原子扣减。

ALTER TABLE dbo.trd_bill ADD settlement_idempotency_key varchar(128) NULL;
GO
CREATE UNIQUE INDEX ux_trd_bill_settlement_idempotency
    ON dbo.trd_bill (settlement_idempotency_key) WHERE settlement_idempotency_key IS NOT NULL;

ALTER TABLE dbo.trd_settlement_quote ADD
    asset_amount decimal(19,4) NOT NULL CONSTRAINT df_trd_quote_asset_amount DEFAULT (0),
    external_payment_amount decimal(19,4) NOT NULL CONSTRAINT df_trd_quote_external_amount DEFAULT (0);

CREATE TABLE dbo.trd_settlement_quote_asset (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_quote_asset PRIMARY KEY,
    quote_id bigint NOT NULL,
    asset_type varchar(16) NOT NULL,
    member_id bigint NOT NULL,
    member_card_id bigint NULL,
    member_card_balance_id bigint NULL,
    bill_line_id bigint NULL,
    service_id bigint NULL,
    quantity decimal(19,4) NOT NULL,
    amount decimal(19,4) NOT NULL,
    asset_version varchar(128) NOT NULL,
    display_name nvarchar(200) NOT NULL,
    sort_no int NOT NULL,
    CONSTRAINT fk_trd_quote_asset_quote FOREIGN KEY (quote_id) REFERENCES dbo.trd_settlement_quote(id),
    CONSTRAINT fk_trd_quote_asset_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_trd_quote_asset_card FOREIGN KEY (member_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_trd_quote_asset_card_balance FOREIGN KEY (member_card_balance_id) REFERENCES dbo.ast_member_card_balance(id),
    CONSTRAINT fk_trd_quote_asset_line FOREIGN KEY (bill_line_id) REFERENCES dbo.trd_bill_line(id),
    CONSTRAINT fk_trd_quote_asset_service FOREIGN KEY (service_id) REFERENCES dbo.cat_service(id),
    CONSTRAINT uq_trd_quote_asset_sort UNIQUE (quote_id, sort_no),
    CONSTRAINT ck_trd_quote_asset_type CHECK (asset_type IN ('BALANCE', 'POINT', 'CARD')),
    CONSTRAINT ck_trd_quote_asset_values CHECK (quantity > 0 AND amount > 0),
    CONSTRAINT ck_trd_quote_asset_reference CHECK (
        (asset_type = 'CARD' AND member_card_id IS NOT NULL AND member_card_balance_id IS NOT NULL
            AND bill_line_id IS NOT NULL AND service_id IS NOT NULL)
        OR (asset_type IN ('BALANCE', 'POINT') AND member_card_id IS NULL
            AND member_card_balance_id IS NULL AND bill_line_id IS NULL)
    )
);

CREATE TABLE dbo.trd_bill_asset_usage (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_bill_asset_usage PRIMARY KEY,
    bill_id bigint NOT NULL,
    asset_type varchar(16) NOT NULL,
    member_id bigint NOT NULL,
    member_card_id bigint NULL,
    member_card_balance_id bigint NULL,
    bill_line_id bigint NULL,
    service_id bigint NULL,
    quantity decimal(19,4) NOT NULL,
    amount decimal(19,4) NOT NULL,
    asset_ledger_id bigint NOT NULL,
    display_name nvarchar(200) NOT NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_trd_asset_usage_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT fk_trd_asset_usage_bill FOREIGN KEY (bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT fk_trd_asset_usage_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_trd_asset_usage_card FOREIGN KEY (member_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_trd_asset_usage_card_balance FOREIGN KEY (member_card_balance_id) REFERENCES dbo.ast_member_card_balance(id),
    CONSTRAINT fk_trd_asset_usage_line FOREIGN KEY (bill_line_id) REFERENCES dbo.trd_bill_line(id),
    CONSTRAINT fk_trd_asset_usage_service FOREIGN KEY (service_id) REFERENCES dbo.cat_service(id),
    CONSTRAINT fk_trd_asset_usage_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT uq_trd_asset_usage UNIQUE (bill_id, asset_type, asset_ledger_id),
    CONSTRAINT ck_trd_asset_usage_type CHECK (asset_type IN ('BALANCE', 'POINT', 'CARD')),
    CONSTRAINT ck_trd_asset_usage_values CHECK (quantity > 0 AND amount > 0)
);

CREATE INDEX ix_trd_asset_usage_member
    ON dbo.trd_bill_asset_usage (member_id, created_at DESC, bill_id);

INSERT INTO dbo.sys_parameter (
    param_group, param_key, value_ciphertext, value_type, is_secret, description
)
VALUES ('ASSET', 'POINTS_PER_YUAN', N'100', 'INTEGER', 0, N'积分抵现比例：多少积分抵扣1元');

-- 验证：新增2张表、账单结算幂等键、试算资产金额，并写入积分兑换比例。
