# 悦·指间管理系统

基于芋道完整单体版重构的美甲及门店管理平台。`backend/` 是芋道 `ruoyi-vue-pro` 单体后端，`frontend/` 是芋道 Vue3 管理端；两端版本来自同一批 `v2026_06` 发布，使用原生 Token、动态菜单和 `/admin-api` 协议。

重构分支为 `refactor/yudao-foundation`。`main@6cae2c8` 的自研前后端分别保存在 `ref/main-frontend-snapshot/`、`ref/main-backend-snapshot/backend/`，用于迁移既有业务规则、测试和 44 个历史 Flyway 脚本；甲方多年以前的源码只保存在本地忽略目录 `ref/legacy-source/`。

## 当前底座

- 后端：[ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro) `master-jdk17@ec3f7cb`，Java 17+、Spring Boot 3.5.15、Spring Security Token、MyBatis Plus、Redis、SQL Server。
- 前端：[yudao-ui-admin-vue3](https://github.com/yudaocode/yudao-ui-admin-vue3) `master@9445977`，Vue 3、TypeScript、Vite、Element Plus、Pinia。
- 当前先启用 `system`、`infra` 和 `yudao-server`；会员、流程、支付、报表、ERP、WMS 等源码已导入，按业务迁移顺序逐个启用。
- SQL Server 首次由官方基线脚本初始化，之后悦指间结构变化统一走 `backend/yudao-server/src/main/resources/db/migration/` 下的 Flyway 脚本。

## 本地启动

依赖：Java 17 或 21、Maven 3.9、Node.js 20.19+、pnpm 10、Docker/Compose。

```bash
make doctor
install -m 600 .env.example .env.local
# 修改 .env.local 中所有 Replace-With... 值
make bootstrap
make infra-up
make db-init
make backend-dev
```

`make db-init` 只会向空的新数据库导入一次芋道 SQL Server 基线；目标库已有用户表但没有悦指间基线标记时会拒绝执行。不要把 `DB_NAME` 指向 main 数据库或甲方旧库。

另开终端启动前端：

```bash
make frontend-dev
```

前端访问 `http://localhost:5173`，后端端口为 `48080`，Swagger 为 `http://localhost:48080/swagger-ui`。首次导入的上游本地种子账号为 `admin/admin123`，只允许用于本机初始化，首次登录后立即修改；生产环境不得使用上游种子数据或默认口令。

## 验证

```bash
make verify
```

## 文档入口

- [项目总计划](docs/项目总计划.md)
- [芋道全栈重构迁移方案](docs/芋道底座重构迁移方案.md)
- [技术实施计划](plan/技术实施计划.md)
- [API 接口计划](plan/API接口.md)
- [页面与路由计划](plan/页面ui.md)
- [数据库计划](plan/数据库表.md)
- [当前开发状态](memory/DEVELOPMENT_STATUS.md)
- [下一步开发](memory/NEXT_ACTIONS.md)
- [重构参考资料](ref/README.md)

跨会话继续开发时，先阅读 [memory/README.md](memory/README.md)。
