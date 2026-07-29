# V202607291370 增加服务项目导出权限

## 对应脚本

`backend/src/main/resources/db/migration/V202607291370__grant_service_catalog_export.sql`

## 变更内容

- 新增`catalog:service:export`按钮权限，独立控制当前门店服务项目批量导出。
- 首版只授予总部管理员，不改变既有服务项目查看和维护权限。

## 执行后检查

```sql
SELECT permission_code, permission_name, api_pattern, http_method
FROM dbo.iam_permission
WHERE permission_code = 'catalog:service:export';

SELECT role.role_code
FROM dbo.iam_role_permission relation
JOIN dbo.iam_role role ON role.id = relation.role_id
JOIN dbo.iam_permission permission ON permission.id = relation.permission_id
WHERE permission.permission_code = 'catalog:service:export' AND relation.effect = 'ALLOW';
```

预期权限唯一，只有`HEADQUARTERS_ADMIN`一条允许关系。

## 执行记录

| 环境 | 状态 | 说明 |
| --- | --- | --- |
| memory开发档 | 已验证 | 当前门店服务查询、CSV内容和页面类型检查通过 |
| SQL Server本地 | 待执行 | 当前无Docker socket权限，待验证权限和真实门店价格查询 |
| 测试/生产 | 未执行 | 上线前由甲方确认成本字段是否允许导出及授权角色 |

## 回滚

共享环境不执行Flyway降级。若甲方不允许导出成本，应先关闭前端入口并移除角色授权，再追加新版本调整导出字段；不得修改已执行脚本。
