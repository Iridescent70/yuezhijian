# V202607291320 初始化通用业务附件

## 对应脚本

`backend/src/main/resources/db/migration/V202607291320__init_business_attachment.sql`

## 变更内容

- 为已有`sys_file_object.status`增加`ACTIVE/DELETED/QUARANTINED`检查约束。
- 新建`sys_file_attachment`，保存文件、业务类型、业务id、门店、分类、创建人和软删除信息。
- 每个文件对象只能绑定一次；业务和门店维度建立仅包含未删除记录的查询索引。
- 文件关联门店、创建人和删除人均建立外键；删除时间与删除人必须同时为空或同时存在。

## 兼容和验证

- 本次不修改既有文件对象数据，也不回填业务附件；旧应用不访问新表，可与新结构短时共存。
- 执行后检查：

```sql
SELECT COUNT(*) AS table_count FROM sys.tables WHERE name = 'sys_file_attachment';
SELECT name, is_disabled FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.sys_file_attachment');
SELECT status, COUNT(*) AS total FROM dbo.sys_file_object GROUP BY status;
```

预期`table_count=1`、两个业务索引可用，既有文件状态均满足新约束。

## 执行记录

| 环境 | 状态 | 说明 |
| --- | --- | --- |
| memory开发档 | 已验证 | 上传、下载、业务绑定隔离、软删除和格式限制均有自动化覆盖 |
| SQL Server/MinIO本地 | 待执行 | 当前缺镜像及Docker socket权限，未声明真实对象存储验收完成 |
| 测试/生产 | 未执行 | 上线前验证DDL、MinIO私有桶、备份和失败对象清理流程 |

## 回滚

共享环境不执行Flyway降级。应用回退时保留新表和约束；若建表阶段失败，从迁移前备份恢复。已上传对象不得手工批量删除，应先按元数据导出对象键清单并人工复核。
