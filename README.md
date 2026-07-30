# 悦·指间管理系统

基于甲方原系统重构的美甲及门店管理平台。后端代码统一放在 `backend/`，PC 前端代码统一放在 `frontend/`。

`refactor/yudao-foundation` 分支使用[芋道 Vue3 管理端](https://github.com/yudaocode/yudao-ui-admin-vue3)作为新前端底座。`main@6cae2c8` 的原前端保存在 `ref/main-frontend-snapshot/` 供页面与 API 迁移对照；甲方旧源码仅保存在本地 `ref/legacy-source/`，不进入 Git。

## 本地启动

依赖：Java 21、Maven 3.9、Node.js 24、pnpm 10、Docker/Compose。

```bash
make doctor
install -m 600 .env.example .env.local
# 修改 .env.local 中所有 Replace-With... 值
make bootstrap
make infra-up
make db-init
make backend-dev
```

另开终端启动前端：

```bash
make frontend-dev
```

应用默认profile和`make backend-dev`都只启动SQL Server持久化模式，并由Flyway校验/升级数据库；缺少`.env.local`、`DB_PASSWORD`或数据库不可用时直接失败，不会静默使用假数据。仅做自动化隔离调试时，才显式执行`make backend-dev-memory`，该模式的所有数据会在进程结束后丢失，不能作为功能完成或验收证据。前端访问`http://localhost:5173`，后端健康检查为`http://localhost:8080/actuator/health`，Swagger为`http://localhost:8080/swagger-ui.html`。

数据库模式启动后执行`make db-smoke`。该命令会通过真实API新建一名标记为“SQL持久化验证”的会员，再直接查询SQL Server中的会员、会员卡、储值账户、积分账户和Flyway版本数；任何一项未落库都会失败。若刚加入`docker`组但当前终端尚未刷新，使用`sg docker -c 'make db-smoke'`。

库存模块执行`make inventory-smoke`：脚本会创建测试礼品，通过盘点确认将源门店库存调整为10，再确认跨店调拨3，最后直接查询SQL Server校验两店余额为7/3、盘点/调拨状态均已确认且存在3条不可变库存流水。该命令会保留带`SQL持久化验证`标记的验收数据，便于重启后复查。

## 开发与验证

```bash
make verify
```

数据库结构只能通过 `backend/src/main/resources/db/migration/` 下的 Flyway SQL 修改；同版本人工说明同步写入 `plan/migration/`。

## 文档入口

- [项目总计划](docs/项目总计划.md)
- [芋道底座重构迁移方案](docs/芋道底座重构迁移方案.md)
- [技术实施计划](plan/技术实施计划.md)
- [API 接口计划](plan/API接口.md)
- [页面与路由计划](plan/页面ui.md)
- [数据库计划](plan/数据库表.md)
- [当前开发状态](memory/DEVELOPMENT_STATUS.md)
- [下一步开发](memory/NEXT_ACTIONS.md)
- [重构参考资料](ref/README.md)

跨会话继续开发时，先阅读 [memory/README.md](memory/README.md)。
