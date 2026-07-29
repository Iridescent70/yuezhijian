# V202607291380 异步任务输入文件

- SQL：`backend/src/main/resources/db/migration/V202607291380__add_async_job_input_file.sql`
- 变更：`sys_async_job`增加可空`input_file_id`，关联`sys_file_object.id`，并建立非空过滤索引。
- 用途：保存异步导入原始文件的私有引用，任务JSON只保存非敏感摘要。
- 清理：任务完成、失败或取消后删除对象内容，并将文件元数据标记为`DELETED`；任务记录继续保留用于审计。
- 回滚：上线前可删除索引、外键和字段；产生导入任务后不得直接回滚，应先停止任务执行并核对输入对象清理结果。
