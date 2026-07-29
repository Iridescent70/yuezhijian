# 悦·指间管理系统

基于甲方原系统重构的美甲及门店管理平台。后端代码统一放在 `backend/`，PC 前端代码统一放在 `frontend/`；旧源码仅保存在本地 `ref/legacy-source/` 供业务拆解，不进入 Git。

## 本地启动

依赖：Java 21、Maven 3.9、Node.js 24、pnpm 10、Docker/Compose。

```bash
make doctor
cp .env.example .env.local
# 修改 .env.local 中所有 Replace-With... 值
make bootstrap
make infra-up
make db-init
make backend-dev-db
```

另开终端启动前端：

```bash
make frontend-dev
```

尚未启动 SQL Server 时，可用 `make backend-dev` 启动 memory profile，完成登录、权限、门店和工作台联调。前端访问 `http://localhost:5173`，后端健康检查为 `http://localhost:8080/actuator/health`，Swagger 为 `http://localhost:8080/swagger-ui.html`。

## 开发与验证

```bash
make verify
```

数据库结构只能通过 `backend/src/main/resources/db/migration/` 下的 Flyway SQL 修改；同版本人工说明同步写入 `plan/migration/`。

## 文档入口

- [项目总计划](docs/项目总计划.md)
- [技术实施计划](plan/技术实施计划.md)
- [API 接口计划](plan/API接口.md)
- [页面与路由计划](plan/页面ui.md)
- [数据库计划](plan/数据库表.md)
- [当前开发状态](memory/DEVELOPMENT_STATUS.md)
- [下一步开发](memory/NEXT_ACTIONS.md)

跨会话继续开发时，先阅读 [memory/README.md](memory/README.md)。
