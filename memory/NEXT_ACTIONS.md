# 下一步开发入口

当前分支：`refactor/yudao-foundation`。业务基线为 `main@6cae2c8`，芋道前端基线为 `yudao-ui-admin-vue3@9445977`。

原后端、44 个 Flyway Migration、合同清单和 `ITER-00` 至 `ITER-52` 记录全部保留。原自研前端位于 `ref/main-frontend-snapshot/`，甲方旧源码位于本地忽略目录 `ref/legacy-source/`。

## 持续开发顺序

1. 联调 Spring Session 登录、CSRF、当前用户、动态菜单、退出和会话失效跳转。
2. 联调当前门店切换与会话续期；确认总部管理员和门店角色的菜单、权限及数据范围。
3. 裁剪未使用的芋道模块与依赖，拆分当前大分块并建立前端测试基线。
4. 迁移组织门店、角色、员工、职务、工位页面，优先建立可复制的 CRUD 适配范式。
5. 迁移会员主档、标签、归属、储值/积分/次卡资产，保持手机号保护和行版本并发控制。
6. 迁移预约、账单、混合结算、冲销、退卡、代金券和提成页面，完成资金与权益回归。
7. 迁移产品、服务、分类、单位、支付方式、回访、反馈、下载中心、通知和审计页面。
8. 完成 `ITER-52` 礼品库存 SQL Server 真实落库验收，再迁移礼品、调拨和盘点页面。
9. 按 `docs/芋道底座重构迁移方案.md` 的 P2 顺序开发合同尚未完成范围。
10. 获取甲方数据库备份，执行字段映射、试迁移、余额/流水/账单/支付对账和上线演练。

## 本轮必须验证

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short --branch
pnpm install
pnpm typecheck
pnpm build
./mvnw test
```

若 Memory 与代码、测试或 Git 状态冲突，以仓库实际结果为准，并立即修正 Memory。
