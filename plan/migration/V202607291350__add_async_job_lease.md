# V202607291350 异步任务运行租约

## 对应脚本

`backend/src/main/resources/db/migration/V202607291350__add_async_job_lease.sql`

## 变更内容

- `sys_async_job`增加`lease_token`、`lease_expires_at`和非空`attempt_count`。
- `attempt_count`默认0并限制在0至10之间，历史任务无需单独回填。
- 新增按状态、租约到期时间、创建时间和id排列的任务调度索引。
- 旧版遗留的`RUNNING`任务因租约为空，会被新版调度器当作失联任务重新领取。

## 执行后检查

```sql
SELECT name, is_nullable
FROM sys.columns
WHERE object_id = OBJECT_ID('dbo.sys_async_job')
  AND name IN ('lease_token', 'lease_expires_at', 'attempt_count');

SELECT name
FROM sys.indexes
WHERE object_id = OBJECT_ID('dbo.sys_async_job')
  AND name = 'ix_sys_async_job_dispatch';

SELECT status, attempt_count, lease_token, lease_expires_at
FROM dbo.sys_async_job
WHERE status IN ('PENDING', 'RUNNING');
```

预期三个字段和调度索引存在，`attempt_count`非空；运行任务有令牌和未来租约，等待任务没有令牌。

## 执行记录

| 环境 | 状态 | 说明 |
| --- | --- | --- |
| memory开发档 | 已验证 | 过期重领、令牌隔离和最大次数失败均有自动化测试 |
| SQL Server本地 | 待执行 | 当前无Docker socket权限，需补真实锁竞争及强杀恢复演练 |
| 测试/生产 | 未执行 | 发布前确认租约时长大于正常任务最大无响应时间 |

## 回滚

共享环境不执行Flyway降级。若新版应用需要临时停用调度，应停止应用实例并保留字段；禁止在仍有`RUNNING`任务时删除租约列。旧应用不认识新字段，但会绕过令牌保护，因此数据库升级后不得回退到旧任务执行器。
