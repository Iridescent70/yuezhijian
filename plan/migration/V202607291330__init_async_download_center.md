# V202607291330 初始化异步任务与下载中心

## 对应脚本

`backend/src/main/resources/db/migration/V202607291330__init_async_download_center.sql`

## 变更内容

- 为`sys_async_job`增加任务名称、所属门店和结果过期时间，已有数据分别按任务类型、首个启用门店和创建后7天回填。
- 新增门店外键及创建人+创建时间查询索引，覆盖任务类型、状态、进度、结果文件和过期时间。
- 新增任务查看、创建、取消权限，授予总部管理员和店长，并在系统管理下增加下载中心菜单。

## 兼容和验证

- 旧任务状态和结果文件引用不变；回填完成后3个新字段改为非空，避免新旧记录出现无门店或无过期时间。
- 执行后检查：

```sql
SELECT COUNT(*) AS missing_count
FROM dbo.sys_async_job
WHERE job_name IS NULL OR store_id IS NULL OR expires_at IS NULL;

SELECT name, is_disabled
FROM sys.indexes
WHERE object_id = OBJECT_ID('dbo.sys_async_job');

SELECT permission_code
FROM dbo.iam_permission
WHERE permission_code LIKE 'system:job:%';
```

预期`missing_count=0`，新增索引可用，3项任务权限存在且下载中心菜单唯一。

## 执行记录

| 环境 | 状态 | 说明 |
| --- | --- | --- |
| memory开发档 | 已验证 | 创建、领取、完成、取消、列表和私有CSV下载有自动化覆盖 |
| SQL Server/MinIO本地 | 待执行 | 当前无Docker socket权限，尚未声明真实并发领取和结果文件联通完成 |
| 测试/生产 | 未执行 | 上线前按实际数据量验证回填锁时长、领取竞争、过期控制和备份恢复 |

## 回滚

共享环境不执行Flyway降级。旧应用可忽略新增列、权限和菜单；如迁移中断，从迁移前备份恢复。结果文件不得直接按桶前缀批量删除，须先对照`result_file_id/error_file_id`和有效期生成清单。
