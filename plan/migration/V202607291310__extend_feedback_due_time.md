# V202607291310 扩展服务反馈处理时限

## 对应脚本

`backend/src/main/resources/db/migration/V202607291310__extend_feedback_due_time.sql`

## 变更内容

- 增加非密钥整数参数`VISIT/SERVICE_FEEDBACK_DUE_HOURS`，默认24，应用限制1~720小时。
- `vis_feedback`增加`due_hours int not null`和`due_at datetime2(3) not null`。
- 已有反馈按`created_at + 24小时`回填，后续建单和重开保存当时采用的参数快照。
- 增加处理时限检查约束、到期时间检查约束及`(status,due_at,store_id,id)`索引。

## 兼容和验证

- 旧应用不读取新增列，可以在迁移后短时间回退；新应用必须等Migration成功后启动。
- 共享环境不修改已执行脚本，发现问题追加新版本修复。
- 空库或脱敏数据执行后检查：

```sql
SELECT COUNT(*) AS missing_due FROM dbo.vis_feedback WHERE due_at IS NULL;
SELECT param_key, value_ciphertext FROM dbo.sys_parameter
WHERE param_group = 'VISIT' AND param_key = 'SERVICE_FEEDBACK_DUE_HOURS';
SELECT status, COUNT(*) AS total FROM dbo.vis_feedback GROUP BY status;
```

预期`missing_due=0`且参数唯一为24。旧库数据量级演练时另行记录回填、约束和索引耗时及锁等待。

## 执行记录

| 环境 | 状态 | 说明 |
| --- | --- | --- |
| memory开发档 | 已验证 | 默认时限、超时筛选、解决和重开已覆盖 |
| SQL Server本地空库 | 待执行 | 当前缺镜像及Docker socket权限 |
| 测试/生产 | 未执行 | 执行前备份，执行后保存Flyway历史和上述核对结果 |

## 回滚

应用回退时保留新参数和两列，不执行共享环境降级。若迁移中途失败，由Flyway标记失败并从迁移前备份恢复；不得在不清楚回填进度时手工删除列或约束。
