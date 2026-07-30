# 重构参考资料

本目录只保存迁移和重构时需要对照的旧实现，不作为新系统的运行入口。

## 目录说明

- `main-frontend-snapshot/`：从 `main@6cae2c8` 保留的自研 Vue 前端快照，用于把已经开发的页面和 API 调用迁移到芋道前端底座。
- `main-backend-snapshot/backend/`：从 `main@6cae2c8` 保留的自研 Spring Boot 后端快照，包含已经开发的业务规则、自动化测试和 44 个 Flyway 数据库迁移，用于逐模块迁移到芋道单体后端。
- `legacy-source/`：甲方多年前的 Java 6、Struts、Spring、Hibernate、JSP 旧系统源码，仅保存在本地并由 `.gitignore` 排除。该目录不得被清理脚本、依赖安装或前端构建覆盖。

## 使用约束

- 新的 PC 管理端统一在 `frontend/` 开发。
- 新的服务端统一在 `backend/` 的芋道单体工程中开发；快照目录只读，不继续追加功能。
- 业务规则优先以 `docs/`、`plan/` 和 main 快照实现为准；甲方旧源码仅用于核对页面、字段、入口和历史行为。
- 旧数据库结构和金额口径必须以甲方提供的数据库备份与书面确认结果为准，不能只依据旧源码定稿。
