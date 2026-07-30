# 下一步开发入口

当前分支：`refactor/yudao-foundation`。业务迁移基线为 `main@6cae2c8`；芋道后端基线为 `ruoyi-vue-pro master-jdk17@ec3f7cb`，前端基线为 `yudao-ui-admin-vue3 master@9445977`。

main 的自研前后端已经分别归档到 `ref/main-backend-snapshot/backend/`、`ref/main-frontend-snapshot/`。44 个旧 Flyway 脚本、合同清单、`ITER-00` 至 `ITER-52` 和本地忽略的甲方旧源码都保留，但均不是新系统运行入口。

## 继续顺序

1. 完成 P0 验证：后端测试、前端类型/构建、Compose 配置、SQL Server 空库初始化和芋道原生登录联调。
2. 建立 `yudao-module-yuezhijian`，保持 API/Biz 分层和芋道模块规范。
3. 输出门店/员工/岗位/角色/数据范围与芋道部门/用户/岗位模型的字段映射。
4. 迁移“门店—员工—岗位—会员主档”首个纵向样板及 main 回归测试。
5. 建立旧 ID 映射、迁移批次、错误和对账表，再迁移会员资产。
6. 按 `docs/芋道底座重构迁移方案.md` 的 P2 顺序迁移 main 已实现功能。
7. P2 稳定后开发合同尚未实现的 P3 范围。
8. 获取甲方数据库备份，完成金额单位确认、试迁移、对账和上线演练。

## 常用验证

```bash
git status --short --branch
./mvnw -f backend/pom.xml test
pnpm install --frozen-lockfile
pnpm typecheck
pnpm build
docker compose --env-file .env.local -f infra/compose.yaml config
```

若 Memory 与仓库实际状态冲突，以代码、测试和 Git 状态为准，并立即修正文档。
