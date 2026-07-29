# 当前开发状态

更新时间：2026-07-29。

## 工程基线

| 内容 | 状态 | 当前结果 |
| --- | --- | --- |
| 后端目录 | DONE | Java代码统一位于 `backend/`，Spring Boot模块可独立启动 |
| 前端目录 | DONE | Vue代码统一位于 `frontend/`，不再保留多余项目嵌套层级 |
| 本地工具链 | DONE | Java 21.0.11、Maven 3.9.15、Node 24.18.0、pnpm 10.34.5 |
| 本地基础设施 | DONE | SQL Server、MinIO及可选Redis已写入 `infra/compose.yaml` |
| SQL Server镜像 | BLOCKED | 本机镜像尚未下载完成，因此空库Flyway执行待补验 |
| 工程命令 | DONE | Maven Wrapper、pnpm workspace、Makefile和GitHub Actions已建立 |

## 已完成代码

| 内容 | 状态 | 说明 |
| --- | --- | --- |
| 公共后端能力 | DONE | 统一响应、traceId、异常处理、OpenAPI和健康检查 |
| 认证权限样板 | DONE | CSRF、登录、当前用户、续期、退出、菜单和权限 |
| 首批业务接口 | DONE | 门店、角色和工作台接口；memory profile可直接联调 |
| PC管理端样板 | DONE | 登录、路由守卫、主框架、菜单、工作台、门店和角色页面 |
| 数据库基线 | DONE | 公共、组织权限、Spring Session及迁移审计共3个Flyway版本 |
| 本地部署配置 | DONE | `.env.example`、Compose、数据库初始化脚本和启动命令 |

## 最近验证

```text
后端：./mvnw test
结果：5 tests，0 failure，0 error，BUILD SUCCESS

前端：pnpm typecheck
结果：通过

前端：pnpm test
结果：1个测试文件、2个测试通过

前端：pnpm build
结果：生产构建通过；主包约1.06 MB，后续改成Element Plus按需引入

基础设施：docker compose --env-file .env.example -f infra/compose.yaml config --quiet
结果：Compose配置校验通过
```

## 当前限制

- 目前业务数据接口仍使用内存实现，SQL Server profile已配置但持久化Mapper尚未接入。
- SQL Server 2022镜像尚未在本机就绪，3个Migration还需在真实空库执行一次并记录结果。
- 齐总版、钇休版、甲方数据库备份和完整数据字典尚未进入当前工作区。
- 数据中心5项口径、AI最终输出范围及第三方支付/短信通道仍需甲方书面确认。
