-- 需求：系统管理-01、系统管理-20、API-COM-001~002、API-VIS-008~010、UI-VIS-002
-- 目的：以统一文件元数据为基础建立私有业务附件关系，服务反馈作为首个接入点。

ALTER TABLE dbo.sys_file_object ADD CONSTRAINT ck_sys_file_status
    CHECK (status IN ('ACTIVE', 'DELETED', 'QUARANTINED'));

CREATE TABLE dbo.sys_file_attachment (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_file_attachment PRIMARY KEY,
    file_id bigint NOT NULL,
    business_type varchar(64) NOT NULL,
    business_id bigint NOT NULL,
    store_id bigint NOT NULL,
    category varchar(64) NOT NULL CONSTRAINT df_sys_file_attachment_category DEFAULT ('GENERAL'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_sys_file_attachment_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    removed_at datetime2(3) NULL,
    removed_by bigint NULL,
    CONSTRAINT uq_sys_file_attachment_file UNIQUE (file_id),
    CONSTRAINT fk_sys_file_attachment_file FOREIGN KEY (file_id) REFERENCES dbo.sys_file_object(id),
    CONSTRAINT fk_sys_file_attachment_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_sys_file_attachment_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_sys_file_attachment_remover FOREIGN KEY (removed_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_sys_file_attachment_business CHECK (LEN(LTRIM(RTRIM(business_type))) > 0),
    CONSTRAINT ck_sys_file_attachment_removed CHECK (
        (removed_at IS NULL AND removed_by IS NULL) OR (removed_at IS NOT NULL AND removed_by IS NOT NULL)
    )
);

CREATE INDEX ix_sys_file_attachment_business
    ON dbo.sys_file_attachment (business_type, business_id, created_at, id)
    INCLUDE (file_id, store_id, category)
    WHERE removed_at IS NULL;

CREATE INDEX ix_sys_file_attachment_store
    ON dbo.sys_file_attachment (store_id, created_at DESC, id DESC)
    WHERE removed_at IS NULL;

-- 不保存公开URL；object_key由服务端生成，下载必须先校验业务权限。
