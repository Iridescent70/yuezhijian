-- 需求：项目总计划/历史数据迁移
-- 目的：记录每次演练和正式迁移的来源、步骤、旧新主键、错误及对账证据。
-- 规则：旧库只读；迁移程序可重跑；正式批次记录不得物理删除。

CREATE TABLE dbo.migration_batch (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_migration_batch PRIMARY KEY,
    batch_no varchar(32) NOT NULL,
    batch_type varchar(16) NOT NULL,
    source_backup_id nvarchar(255) NOT NULL,
    source_backup_sha256 char(64) NULL,
    source_cutoff_at datetime2(3) NOT NULL,
    started_at datetime2(3) NULL,
    finished_at datetime2(3) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_migration_batch_status DEFAULT ('PENDING'),
    initiated_by bigint NULL,
    code_version varchar(64) NOT NULL,
    schema_version varchar(64) NOT NULL,
    note nvarchar(2000) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_migration_batch_created_at DEFAULT (sysdatetime()),
    CONSTRAINT uq_migration_batch_no UNIQUE (batch_no),
    CONSTRAINT ck_migration_batch_type CHECK (batch_type IN ('DRY_RUN', 'FORMAL')),
    CONSTRAINT ck_migration_batch_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'ROLLED_BACK'))
);

CREATE TABLE dbo.migration_step (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_migration_step PRIMARY KEY,
    batch_id bigint NOT NULL,
    step_code varchar(64) NOT NULL,
    source_table varchar(128) NOT NULL,
    target_table varchar(128) NOT NULL,
    started_at datetime2(3) NULL,
    finished_at datetime2(3) NULL,
    read_count bigint NOT NULL CONSTRAINT df_migration_step_read DEFAULT (0),
    insert_count bigint NOT NULL CONSTRAINT df_migration_step_insert DEFAULT (0),
    update_count bigint NOT NULL CONSTRAINT df_migration_step_update DEFAULT (0),
    skip_count bigint NOT NULL CONSTRAINT df_migration_step_skip DEFAULT (0),
    error_count bigint NOT NULL CONSTRAINT df_migration_step_error DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_migration_step_status DEFAULT ('PENDING'),
    checksum char(64) NULL,
    note nvarchar(2000) NULL,
    CONSTRAINT uq_migration_step_batch_code UNIQUE (batch_id, step_code),
    CONSTRAINT fk_migration_step_batch FOREIGN KEY (batch_id) REFERENCES dbo.migration_batch(id),
    CONSTRAINT ck_migration_step_counts CHECK (
        read_count >= 0 AND insert_count >= 0 AND update_count >= 0 AND skip_count >= 0 AND error_count >= 0
    ),
    CONSTRAINT ck_migration_step_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'SKIPPED'))
);

CREATE INDEX ix_migration_step_batch_status
    ON dbo.migration_step (batch_id, status, id);

CREATE TABLE dbo.legacy_id_map (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_legacy_id_map PRIMARY KEY,
    batch_id bigint NOT NULL,
    entity_type varchar(64) NOT NULL,
    legacy_table varchar(128) NOT NULL,
    legacy_id varchar(128) NOT NULL,
    target_table varchar(128) NOT NULL,
    target_id bigint NOT NULL,
    source_checksum char(64) NULL,
    migrated_at datetime2(3) NOT NULL CONSTRAINT df_legacy_id_map_at DEFAULT (sysdatetime()),
    CONSTRAINT uq_legacy_id_map_source UNIQUE (batch_id, legacy_table, legacy_id),
    CONSTRAINT fk_legacy_id_map_batch FOREIGN KEY (batch_id) REFERENCES dbo.migration_batch(id)
);

CREATE INDEX ix_legacy_id_map_target
    ON dbo.legacy_id_map (target_table, target_id);

CREATE TABLE dbo.migration_error (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_migration_error PRIMARY KEY,
    batch_id bigint NOT NULL,
    step_id bigint NULL,
    legacy_table varchar(128) NOT NULL,
    legacy_id varchar(128) NULL,
    error_code varchar(64) NOT NULL,
    error_message nvarchar(2000) NOT NULL,
    source_snapshot_json nvarchar(max) NULL,
    resolution_status varchar(32) NOT NULL CONSTRAINT df_migration_error_status DEFAULT ('OPEN'),
    resolved_by bigint NULL,
    resolved_at datetime2(3) NULL,
    resolution_note nvarchar(2000) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_migration_error_created_at DEFAULT (sysdatetime()),
    CONSTRAINT fk_migration_error_batch FOREIGN KEY (batch_id) REFERENCES dbo.migration_batch(id),
    CONSTRAINT fk_migration_error_step FOREIGN KEY (step_id) REFERENCES dbo.migration_step(id),
    CONSTRAINT ck_migration_error_status CHECK (resolution_status IN ('OPEN', 'IGNORED', 'FIXED', 'RETRIED'))
);

CREATE INDEX ix_migration_error_batch_status
    ON dbo.migration_error (batch_id, resolution_status, id);

CREATE TABLE dbo.migration_reconciliation (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_migration_reconciliation PRIMARY KEY,
    batch_id bigint NOT NULL,
    metric_code varchar(64) NOT NULL,
    scope_type varchar(32) NOT NULL,
    scope_id varchar(64) NOT NULL,
    source_value decimal(38,4) NOT NULL,
    target_value decimal(38,4) NOT NULL,
    difference decimal(38,4) NOT NULL,
    tolerance decimal(38,4) NOT NULL CONSTRAINT df_migration_reconciliation_tolerance DEFAULT (0),
    result varchar(16) NOT NULL,
    evidence_file_id bigint NULL,
    checked_at datetime2(3) NOT NULL CONSTRAINT df_migration_reconciliation_checked DEFAULT (sysdatetime()),
    checked_by bigint NULL,
    note nvarchar(2000) NULL,
    CONSTRAINT uq_migration_reconciliation UNIQUE (batch_id, metric_code, scope_type, scope_id),
    CONSTRAINT fk_migration_reconciliation_batch FOREIGN KEY (batch_id) REFERENCES dbo.migration_batch(id),
    CONSTRAINT fk_migration_reconciliation_file FOREIGN KEY (evidence_file_id) REFERENCES dbo.sys_file_object(id),
    CONSTRAINT ck_migration_reconciliation_result CHECK (result IN ('PASS', 'FAIL', 'REVIEW')),
    CONSTRAINT ck_migration_reconciliation_tolerance CHECK (tolerance >= 0)
);

CREATE INDEX ix_migration_reconciliation_result
    ON dbo.migration_reconciliation (batch_id, result, metric_code);

-- 验证 SQL：预期 object_count = 5。
-- SELECT COUNT(*) AS object_count FROM sys.tables
-- WHERE name IN ('migration_batch','migration_step','legacy_id_map','migration_error','migration_reconciliation');
