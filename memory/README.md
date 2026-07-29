# 项目开发 Memory

本目录用于跨会话保存悦指间项目的稳定上下文和开发进度。新会话开始开发前，按以下顺序阅读：

1. [PROJECT_CONTEXT.md](./PROJECT_CONTEXT.md)：项目范围、技术基线、源码情况和不能随意改变的决定。
2. [DEVELOPMENT_STATUS.md](./DEVELOPMENT_STATUS.md)：当前已经完成、正在进行和验证结果。
3. [NEXT_ACTIONS.md](./NEXT_ACTIONS.md)：下一次会话从哪里继续、按什么顺序执行。

详细需求仍以 `docs/` 为准，接口、页面和数据库设计仍以 `plan/` 为准。Memory只负责“现在做到哪里”，不能替代正式计划、ADR、Migration或测试报告。

## 维护规则

- 每次开发开始：读取本目录，并用仓库实际状态验证记录是否仍然准确。
- 每次开发结束：更新状态、验证命令、未完成内容和下一步；不要只写“已完成”。
- 技术决策：正式写入 `plan/decisions/`，Memory只保留结论和链接。
- 数据库变化：正式写入Flyway SQL和 `plan/migration/`，Memory只记录最新版本号。
- 禁止记录：密码、Token、API Key、生产地址中的密钥参数、会员真实敏感数据。
- 记录状态统一使用：`TODO`、`DOING`、`BLOCKED`、`DONE`。

## 会话交接模板

```text
日期：
当前分支/提交：
本次完成：
本次验证：
当前阻塞：
未提交文件：
下一步第一条命令：
```
