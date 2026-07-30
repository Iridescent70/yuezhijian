-- 需求：API-TRD-006/007、UI-TRD-004
-- 目的：支持未结算账单行维护、软删除、整单优惠分摊及历史批次追踪。

ALTER TABLE dbo.trd_bill_line ADD
    line_status varchar(16) NOT NULL CONSTRAINT df_trd_bill_line_status DEFAULT ('ACTIVE'),
    removed_at datetime2(3) NULL,
    removed_by bigint NULL;
GO

ALTER TABLE dbo.trd_bill_line ADD
    CONSTRAINT ck_trd_bill_line_status CHECK (line_status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT fk_trd_bill_line_removed_by FOREIGN KEY (removed_by) REFERENCES dbo.iam_user(id);
GO

CREATE INDEX ix_trd_bill_line_active
    ON dbo.trd_bill_line (bill_id, line_status, line_no) INCLUDE (original_amount, receivable_amount);

CREATE TABLE dbo.trd_bill_discount (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_bill_discount PRIMARY KEY,
    batch_no varchar(32) NOT NULL,
    bill_id bigint NOT NULL,
    bill_line_id bigint NOT NULL,
    discount_type varchar(32) NOT NULL,
    discount_value decimal(19,6) NOT NULL,
    original_amount decimal(19,4) NOT NULL,
    discount_amount decimal(19,4) NOT NULL,
    reason nvarchar(500) NOT NULL,
    authorization_user_id bigint NOT NULL,
    rule_snapshot_json nvarchar(max) NOT NULL CONSTRAINT df_trd_discount_rule DEFAULT (N'{}'),
    active bit NOT NULL CONSTRAINT df_trd_discount_active DEFAULT (1),
    superseded_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_trd_discount_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT uq_trd_discount_batch_line UNIQUE (batch_no, bill_line_id),
    CONSTRAINT fk_trd_discount_bill FOREIGN KEY (bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT fk_trd_discount_line FOREIGN KEY (bill_line_id) REFERENCES dbo.trd_bill_line(id),
    CONSTRAINT fk_trd_discount_authorizer FOREIGN KEY (authorization_user_id) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_trd_discount_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_trd_discount_type CHECK (discount_type IN ('AMOUNT', 'RATE')),
    CONSTRAINT ck_trd_discount_amount CHECK (
        discount_value >= 0 AND original_amount >= 0 AND discount_amount >= 0
        AND discount_amount <= original_amount
    )
);

CREATE UNIQUE INDEX ux_trd_discount_active_line
    ON dbo.trd_bill_discount (bill_line_id) WHERE active = 1;
CREATE INDEX ix_trd_discount_bill_batch
    ON dbo.trd_bill_discount (bill_id, active, created_at DESC, id DESC);

-- 验证：账单行新增软删除状态；优惠按批次逐行记录，任一账单行最多一条有效优惠。
