-- 需求：API-AST-011~012、次卡管理-02
-- 目的：建立按剩余办卡单价折算的换卡试算、补差支付和新旧卡完整流水。

ALTER TABLE dbo.ast_member_card DROP CONSTRAINT fk_ast_member_card_order;
ALTER TABLE dbo.ast_member_card ALTER COLUMN source_order_id bigint NULL;
ALTER TABLE dbo.ast_member_card ADD CONSTRAINT fk_ast_member_card_order
    FOREIGN KEY (source_order_id) REFERENCES dbo.ast_card_sale_order(id);

CREATE TABLE dbo.ast_card_exchange_quote (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_card_exchange_quote PRIMARY KEY,
    quote_no varchar(32) NOT NULL,
    old_card_id bigint NOT NULL,
    target_card_type_id bigint NOT NULL,
    old_remaining_times decimal(19,4) NOT NULL,
    old_remaining_value decimal(19,4) NOT NULL,
    new_card_value decimal(19,4) NOT NULL,
    difference_amount decimal(19,4) NOT NULL,
    old_card_row_version varbinary(8) NOT NULL,
    target_card_type_row_version varbinary(8) NOT NULL,
    expires_at datetime2(3) NOT NULL,
    used_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ast_exchange_quote_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT uq_ast_exchange_quote_no UNIQUE (quote_no),
    CONSTRAINT fk_ast_exchange_quote_card FOREIGN KEY (old_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_ast_exchange_quote_type FOREIGN KEY (target_card_type_id) REFERENCES dbo.cat_card_type(id),
    CONSTRAINT fk_ast_exchange_quote_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_exchange_quote_amount CHECK (
        old_remaining_times > 0 AND old_remaining_value >= 0
        AND new_card_value >= old_remaining_value
        AND difference_amount = new_card_value - old_remaining_value
    )
);

CREATE TABLE dbo.ast_card_exchange (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_card_exchange PRIMARY KEY,
    exchange_no varchar(32) NOT NULL,
    quote_id bigint NOT NULL,
    old_card_id bigint NOT NULL,
    new_card_id bigint NOT NULL,
    member_id bigint NOT NULL,
    old_remaining_value decimal(19,4) NOT NULL,
    new_card_value decimal(19,4) NOT NULL,
    difference_amount decimal(19,4) NOT NULL,
    handled_store_id bigint NOT NULL,
    handled_employee_id bigint NULL,
    idempotency_key varchar(128) NOT NULL,
    executed_at datetime2(3) NOT NULL CONSTRAINT df_ast_exchange_executed DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT uq_ast_exchange_no UNIQUE (exchange_no),
    CONSTRAINT uq_ast_exchange_quote UNIQUE (quote_id),
    CONSTRAINT uq_ast_exchange_old_card UNIQUE (old_card_id),
    CONSTRAINT uq_ast_exchange_new_card UNIQUE (new_card_id),
    CONSTRAINT uq_ast_exchange_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_ast_exchange_quote FOREIGN KEY (quote_id) REFERENCES dbo.ast_card_exchange_quote(id),
    CONSTRAINT fk_ast_exchange_old_card FOREIGN KEY (old_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_ast_exchange_new_card FOREIGN KEY (new_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_ast_exchange_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_ast_exchange_store FOREIGN KEY (handled_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_ast_exchange_employee FOREIGN KEY (handled_employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_ast_exchange_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_exchange_amount CHECK (
        old_remaining_value >= 0 AND new_card_value >= old_remaining_value
        AND difference_amount = new_card_value - old_remaining_value
    )
);

CREATE TABLE dbo.ast_card_exchange_payment (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_exchange_payment PRIMARY KEY,
    exchange_id bigint NOT NULL,
    payment_method_id bigint NOT NULL,
    amount decimal(19,4) NOT NULL,
    external_reference varchar(128) NULL,
    sort_no int NOT NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ast_exchange_payment_created DEFAULT (sysdatetime()),
    CONSTRAINT fk_ast_exchange_payment_exchange FOREIGN KEY (exchange_id) REFERENCES dbo.ast_card_exchange(id),
    CONSTRAINT fk_ast_exchange_payment_method FOREIGN KEY (payment_method_id) REFERENCES dbo.cat_payment_method(id),
    CONSTRAINT uq_ast_exchange_payment_sort UNIQUE (exchange_id, sort_no),
    CONSTRAINT uq_ast_exchange_payment_method UNIQUE (exchange_id, payment_method_id),
    CONSTRAINT ck_ast_exchange_payment_amount CHECK (amount > 0)
);

CREATE INDEX ix_ast_exchange_member_time ON dbo.ast_card_exchange (member_id, executed_at DESC, id DESC);

-- 验证：旧卡必须一次性关闭并清零，新卡按目标卡规则入账，补差支付合计必须等于difference_amount。
