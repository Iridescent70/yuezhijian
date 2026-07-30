IF OBJECT_ID(N'dbo.system_users', N'U') IS NULL
    THROW 50000, 'Missing Yudao SQL Server baseline. Run make db-init before starting the application.', 1;

IF OBJECT_ID(N'dbo.yuezhijian_schema_baseline', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.yuezhijian_schema_baseline
    (
        id bigint NOT NULL PRIMARY KEY,
        upstream_repository nvarchar(255) NOT NULL,
        upstream_commit char(40) NOT NULL,
        imported_at datetime2 NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

    INSERT INTO dbo.yuezhijian_schema_baseline (id, upstream_repository, upstream_commit)
    VALUES (1, N'https://github.com/YunaiV/ruoyi-vue-pro', N'ec3f7cbf73e88514a70a6b59d365092ee470603d');
END;
