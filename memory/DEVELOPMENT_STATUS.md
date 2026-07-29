# 当前开发状态

更新时间：2026-07-29。

## 工程与环境

| 内容 | 状态 | 当前结果 |
| --- | --- | --- |
| 工程目录 | DONE | 后端 `backend/`，PC前端 `frontend/` |
| 工具链 | DONE | Java 21.0.11、Maven 3.9.15、Node 24.18.0、pnpm 10.34.5 |
| 本地基础设施配置 | DONE | SQL Server、MinIO及可选Redis Compose已建立 |
| SQL Server镜像 | BLOCKED | 本机尚无2022镜像，真实空库Migration验证待执行 |
| CI与工程命令 | DONE | Maven Wrapper、pnpm workspace、Makefile、GitHub Actions可用 |

## 已完成模块

| 模块 | 状态 | 当前能力 |
| --- | --- | --- |
| 公共后端 | DONE | 统一响应、traceId、异常、分页、OpenAPI、健康检查 |
| 认证权限 | DONE | CSRF、登录、当前用户、续期、退出；memory与SQL Server双实现 |
| 数据库认证 | DONE | MyBatis用户/角色/权限/菜单/门店查询，受控管理员初始化 |
| 会员基础闭环 | DONE | 分页查询、详情、建档；会员卡、储值和积分账户同步创建 |
| 会员PC页面 | DONE | `/app/members`、`/new`、`/:memberId` 三个页面 |
| 预约前置主数据 | DONE | 职务、员工、工位、服务分类、服务项目及门店售价API |
| 主数据PC页面 | DONE | `/app/system/employees`、`/workstations`、`/app/catalog/services` |
| 预约核心闭环 | DONE | 查询、可约时段、创建、改期、详情和7态状态机，支持冲突与幂等 |
| 预约PC页面 | DONE | `/app/appointments`、`/calendar`、`/new`，含详情处理和可约时段 |
| 开单结算闭环 | DONE | 手工/预约转账单、项目快照、混合支付试算、结算幂等和作废 |
| 账单PC页面 | DONE | `/app/bills`、`/new`、`/:id`、`/:id/settle` 四个页面 |
| 敏感字段保护 | DONE | AES-256-GCM密文、带pepper检索哈希、手机号接口脱敏 |
| 数据库版本 | DONE | 0900、0910、1030、1100、1110、1120、1130共7个Migration脚本及人工记录 |
| 前端按需加载 | DONE | Element Plus按需加载，最大公共JS约163 KB |

## 最近验证

```text
./mvnw test
  21 tests，0 failure，0 error

pnpm test
  1个测试文件、2个测试通过

pnpm build
  含类型检查并通过；最大公共JS约164.04 KB（原约1.06 MB）

docker compose --env-file .env.example -f infra/compose.yaml config --quiet
  通过
```

## 当前限制

- SQL Server镜像未就绪，7个Migration及数据库版Mapper尚未在真实空库执行。
- 甲方数据库备份、完整数据字典、齐总版和钇休版代码尚未进入工作区。
- 数据中心5项、AI最终输出范围、支付/短信通道及部分计算口径仍需甲方确认。
- 当前会员范围是API-MEM-001~003；编辑、冻结、标签、归属调整和资产流水尚未开发。
- 当前主数据范围只覆盖预约所需查询和新建；编辑、停用、职务/分类维护及产品资料尚未开发。
- 预约排班、候补队列和日/周拖拽视图尚未开发。
- 预约到店转账单已完成；次卡、储值、积分、优惠券及结算后回访/提成尚未接入。
