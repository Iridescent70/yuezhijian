-- 需求：ITER-00，本地开发基线
-- 目的：建立公共配置、文件、异步任务、幂等与审计能力。
-- 恢复：上线前备份；失败时恢复数据库，不在共享环境执行 Flyway clean。
-- 验证：文件末尾查询应返回 6 张表。

CREATE TABLE dbo.sys_parameter (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_parameter PRIMARY KEY,
    param_group varchar(64) NOT NULL,
    param_key varchar(128) NOT NULL,
    value_ciphertext nvarchar(max) NULL,
    value_type varchar(32) NOT NULL CONSTRAINT df_sys_parameter_value_type DEFAULT ('STRING'),
    is_secret bit NOT NULL CONSTRAINT df_sys_parameter_is_secret DEFAULT (0),
    description nvarchar(500) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_sys_parameter_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_sys_parameter_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_sys_parameter_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_sys_parameter_group_key UNIQUE (param_group, param_key),
    CONSTRAINT ck_sys_parameter_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE dbo.sys_dictionary (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_dictionary PRIMARY KEY,
    dict_type varchar(64) NOT NULL,
    item_code varchar(64) NOT NULL,
    item_name nvarchar(100) NOT NULL,
    item_value nvarchar(500) NULL,
    sort_no int NOT NULL CONSTRAINT df_sys_dictionary_sort DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_sys_dictionary_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_sys_dictionary_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_sys_dictionary_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_sys_dictionary_type_code UNIQUE (dict_type, item_code),
    CONSTRAINT ck_sys_dictionary_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_sys_dictionary_type_sort
    ON dbo.sys_dictionary (dict_type, status, sort_no);

CREATE TABLE dbo.sys_file_object (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_file_object PRIMARY KEY,
    object_key varchar(500) NOT NULL,
    original_name nvarchar(255) NOT NULL,
    content_type varchar(128) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 char(64) NOT NULL,
    purpose varchar(64) NOT NULL,
    owner_user_id bigint NULL,
    access_level varchar(32) NOT NULL CONSTRAINT df_sys_file_access DEFAULT ('PRIVATE'),
    expires_at datetime2(3) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_sys_file_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_sys_file_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_sys_file_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_sys_file_object_key UNIQUE (object_key),
    CONSTRAINT ck_sys_file_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_sys_file_access CHECK (access_level IN ('PRIVATE', 'STORE', 'ORGANIZATION', 'PUBLIC'))
);

CREATE INDEX ix_sys_file_owner_created
    ON dbo.sys_file_object (owner_user_id, created_at DESC);

CREATE TABLE dbo.sys_async_job (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_async_job PRIMARY KEY,
    job_no varchar(32) NOT NULL,
    job_type varchar(64) NOT NULL,
    request_json nvarchar(max) NULL,
    progress int NOT NULL CONSTRAINT df_sys_async_job_progress DEFAULT (0),
    success_count int NOT NULL CONSTRAINT df_sys_async_job_success DEFAULT (0),
    failure_count int NOT NULL CONSTRAINT df_sys_async_job_failure DEFAULT (0),
    result_file_id bigint NULL,
    error_file_id bigint NULL,
    started_at datetime2(3) NULL,
    finished_at datetime2(3) NULL,
    error_message nvarchar(1000) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_sys_async_job_status DEFAULT ('PENDING'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_sys_async_job_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_sys_async_job_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_sys_async_job_no UNIQUE (job_no),
    CONSTRAINT fk_sys_async_job_result_file FOREIGN KEY (result_file_id) REFERENCES dbo.sys_file_object(id),
    CONSTRAINT fk_sys_async_job_error_file FOREIGN KEY (error_file_id) REFERENCES dbo.sys_file_object(id),
    CONSTRAINT ck_sys_async_job_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_sys_async_job_counts CHECK (success_count >= 0 AND failure_count >= 0),
    CONSTRAINT ck_sys_async_job_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED'))
);

CREATE INDEX ix_sys_async_job_type_status
    ON dbo.sys_async_job (job_type, status, created_at DESC);

CREATE TABLE dbo.sys_idempotency_record (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_idempotency_record PRIMARY KEY,
    scope varchar(64) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    resource_type varchar(64) NULL,
    resource_id varchar(64) NULL,
    response_code varchar(32) NULL,
    response_json nvarchar(max) NULL,
    expires_at datetime2(3) NOT NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_sys_idempotency_created_at DEFAULT (sysdatetime()),
    CONSTRAINT uq_sys_idempotency_scope_key UNIQUE (scope, idempotency_key)
);

CREATE INDEX ix_sys_idempotency_expires
    ON dbo.sys_idempotency_record (expires_at);

CREATE TABLE dbo.sys_audit_log (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_audit_log PRIMARY KEY,
    trace_id varchar(64) NOT NULL,
    user_id bigint NULL,
    store_id bigint NULL,
    module varchar(64) NOT NULL,
    action varchar(64) NOT NULL,
    object_type varchar(64) NULL,
    object_id varchar(64) NULL,
    before_json nvarchar(max) NULL,
    after_json nvarchar(max) NULL,
    result varchar(16) NOT NULL,
    error_code varchar(32) NULL,
    ip varchar(64) NULL,
    occurred_at datetime2(3) NOT NULL CONSTRAINT df_sys_audit_occurred_at DEFAULT (sysdatetime()),
    CONSTRAINT ck_sys_audit_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX ix_sys_audit_occurred
    ON dbo.sys_audit_log (occurred_at DESC);
CREATE INDEX ix_sys_audit_object
    ON dbo.sys_audit_log (object_type, object_id, occurred_at DESC);
CREATE INDEX ix_sys_audit_user
    ON dbo.sys_audit_log (user_id, occurred_at DESC);

-- 验证 SQL：预期 object_count = 6。
-- SELECT COUNT(*) AS object_count FROM sys.tables
-- WHERE name IN ('sys_parameter','sys_dictionary','sys_file_object','sys_async_job','sys_idempotency_record','sys_audit_log');
