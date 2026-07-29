# V202607291340 增加会员导出权限

## 对应脚本

`backend/src/main/resources/db/migration/V202607291340__grant_member_export.sql`

## 变更内容

- 新增`member:member:export`按钮权限，独立控制本门店会员名单导出。
- 将权限授予总部管理员和店长；不改变原会员查看、编辑和批量操作权限。

## 兼容和验证

本次不改业务表、不回填会员数据。执行后检查：

```sql
SELECT permission_code, permission_name, api_pattern, http_method
FROM dbo.iam_permission
WHERE permission_code = 'member:member:export';

SELECT role.role_code
FROM dbo.iam_role_permission relation
JOIN dbo.iam_role role ON role.id = relation.role_id
JOIN dbo.iam_permission permission ON permission.id = relation.permission_id
WHERE permission.permission_code = 'member:member:export' AND relation.effect = 'ALLOW';
```

预期权限唯一，总部管理员和店长各有一条允许关系。

## 执行记录

| 环境 | 状态 | 说明 |
| --- | --- | --- |
| memory开发档 | 已验证 | 会员任务执行、当前门店范围和手机号脱敏有接口测试覆盖 |
| SQL Server/MinIO本地 | 待执行 | 当前无Docker socket权限，待验证真实权限查询及结果文件 |
| 测试/生产 | 未执行 | 上线前由甲方确认导出角色、脱敏口径和审计要求 |

## 回滚

共享环境不执行Flyway降级。旧应用忽略新权限；若需停止导出，应移除角色授权并关闭前端入口，不删除权限主记录和历史任务。
