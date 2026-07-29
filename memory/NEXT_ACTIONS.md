# 下一步开发入口

当前工程基线：`backend/` + `frontend/`；当前迭代记录见 `plan/iterations/ITER-00-本地开发基线.md`。

## 下次会话继续顺序

1. SQL Server镜像就绪后，执行 `make infra-up`、`make db-init`、`make backend-dev-db`，验证3个Flyway Migration及 `flyway_schema_history`。
2. 将当前内存版用户、角色、权限、门店查询替换为MyBatis持久化实现，并增加安全的管理员初始化命令。
3. 对照甲方旧库备份补齐旧表映射，确认ID、金额单位、状态枚举、会员资产和账单关系。
4. 开始会员模块纵向开发：会员列表、详情、建档、标签、归属门店、储值/积分/次卡只读资产视图。
5. 前端改为Element Plus按需引入，控制首屏包体；为会员纵向流程增加接口和页面测试。

## 下次会话第一组检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -1 --oneline
sed -n '1,240p' memory/DEVELOPMENT_STATUS.md
sed -n '1,220p' memory/NEXT_ACTIONS.md
find backend/src frontend/src -type f | sort
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并在本次会话内修正Memory。
