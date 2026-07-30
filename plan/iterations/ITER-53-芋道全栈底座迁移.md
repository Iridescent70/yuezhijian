# ITER-53 芋道全栈底座迁移

| 项目 | 内容 |
| --- | --- |
| 迭代周期 | 2026-07-30—进行中 |
| 执行/确认方式 | AI 开发，人工确认 |
| 关联里程碑 | 全栈重构 P0 |
| 计划状态 | 执行中 |

## 一、目标

在不丢失 `main@6cae2c8` 业务资产的前提下，以芋道完整单体后端和 Vue3 管理端共同替换旧自研运行底座，建立可追溯、可构建、可初始化并可继续迁移业务的工程基线。

## 二、范围与验收

| 编号 | 范围 | 验收证据 | 状态 |
| --- | --- | --- | --- |
| REF-P0-01 | 保存 main 自研前端、后端、文档和旧源码边界 | `ref/README.md`、Git 历史 | DONE |
| REF-P0-02 | 导入芋道 Vue3 前端 | `frontend/UPSTREAM.md`、subtree 提交 | DONE |
| REF-P0-03 | 导入芋道完整单体后端 | `backend/UPSTREAM.md`、subtree 提交 | DONE |
| REF-P0-04 | 使用芋道原生 Token、动态菜单和 `/admin-api` | 前端类型检查、运行联调 | DOING |
| REF-P0-05 | 建立 SQL Server、Redis 和安全 profile | 配置检查、容器启动、登录验证 | DOING |
| REF-P0-06 | 建立一次性芋道 SQL 基线和 Flyway 增量入口 | 空库初始化、二次执行不覆盖、Flyway 记录 | DOING |
| REF-P0-07 | 建立 Maven、pnpm 和 CI 构建基线 | `make verify` | DONE |
| REF-P0-08 | 更新迁移映射和跨会话状态 | docs/plan/memory 一致性检查 | DONE |

本期不声称完成 main 业务迁移，也不直接启用全部可选模块。甲方本地旧源码继续由 Git 忽略。

## 三、技术变更

| 类型 | 变更 |
| --- | --- |
| 前端 | `frontend/` 为 `yudao-ui-admin-vue3@9445977`，保留悦指间品牌和根 pnpm workspace |
| 后端 | `backend/` 为 `ruoyi-vue-pro master-jdk17@ec3f7cb`，当前启用 system/infra/server |
| API | 从临时旧后端适配恢复为芋道 `/admin-api`、Bearer Token、刷新 Token、字典和动态菜单 |
| 数据库 | SQL Server 官方全量脚本一次性导入；悦指间后续变化由 Flyway 管理 |
| 环境 | 本地必需 SQL Server + Redis；MinIO 保留供文件模块接入 |
| 回滚 | 生产继续使用 main；重构分支可从前后端快照提取业务，不回写快照 |

## 四、测试计划

1. `./mvnw -f backend/pom.xml test`：芋道当前启用模块测试通过。
2. `pnpm install --frozen-lockfile`、`pnpm typecheck`、`pnpm build`：前端依赖、类型和构建通过。
3. `docker compose config`：SQL Server、Redis、MinIO 配置有效。
4. 全新数据库执行 `make db-init` 成功；再次执行必须跳过上游破坏性全量脚本。
5. 启动后使用本地种子账号验证登录、刷新 Token、菜单、用户信息、退出和 Swagger。
6. 检查数据库存在 `flyway_schema_history` 和 `yuezhijian_schema_baseline`。

## 五、风险

| 风险 | 处理 |
| --- | --- |
| 上游完整仓库模块多、构建和前端包较大 | 先只启用 system/infra；业务模块按迁移顺序开启并裁剪菜单 |
| 官方 SQL 是全量 DROP/CREATE 脚本 | 仅允许空库首次导入，用标记表阻止重复执行 |
| main 表结构与芋道模型重叠 | 先出字段/ID/权限映射，不直接重放 44 个旧 Migration |
| 上游示例配置含演示服务和示例密钥 | 默认切换 `yuezhijian` profile，真实密钥只从部署环境注入 |
| main 的 DONE 容易被误认为新系统 DONE | 状态文档明确区分“迁移资产”和“芋道运行完成” |

## 六、完成定义

P0 只有在前后端构建、空库初始化、服务启动和原生登录链路全部通过后才能标记 DONE。之后进入 P1：建立悦指间业务模块，并迁移门店—员工—岗位—会员主档首个纵向样板。
