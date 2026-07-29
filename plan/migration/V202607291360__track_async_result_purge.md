# V202607291360 记录异步结果清理

## 对应脚本

`backend/src/main/resources/db/migration/V202607291360__track_async_result_purge.sql`

## 变更内容

- `sys_async_job`增加可空`result_purged_at`，记录结果对象完成物理清理的时间。
- 新增到期扫描过滤索引，覆盖状态和结果文件id。
- 旧任务无需回填；未过期任务保持为空，已过期任务由清理调度逐批处理。

## 执行后检查

```sql
SELECT name, is_nullable
FROM sys.columns
WHERE object_id = OBJECT_ID('dbo.sys_async_job') AND name = 'result_purged_at';

SELECT name, filter_definition
FROM sys.indexes
WHERE object_id = OBJECT_ID('dbo.sys_async_job')
  AND name = 'ix_sys_async_job_result_cleanup';

SELECT TOP (20) job_no, expires_at, result_file_id, result_purged_at
FROM dbo.sys_async_job
ORDER BY expires_at;
```

## 执行记录

| 环境 | 状态 | 说明 |
| --- | --- | --- |
| memory开发档 | 已验证 | 到期清理、不可下载、记录保留和重复执行均有测试覆盖 |
| SQL Server/MinIO本地 | 待执行 | 当前无Docker socket权限，需验证过滤索引、真实对象删除和故障重试 |
| 测试/生产 | 未执行 | 上线前核对7天保留期及存储侧版本保留策略 |

## 回滚

共享环境不删除字段和索引。临时停用时将`JOB_CLEANUP_DELAY_MS`调大或停止调度实例；已经物理删除的结果文件不可从应用恢复，只能从存储侧备份或版本记录恢复。
