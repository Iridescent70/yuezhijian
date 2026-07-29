# 下一步开发入口

已完成迭代：`ITER-00`至`ITER-04`，当前核心链路为登录→会员→预约→到店→账单→混合支付结算。

## 持续开发顺序

1. 建立储值、积分不可变流水和次卡定义/会员次卡/扣次流水，补齐当前账户与历史记录。
2. 开发会员充值、积分调整、次卡售卡与项目匹配，并将资产选项接入结算试算。
3. 结算事务同步扣减储值/积分/次卡并写不可变流水，失败时整单回滚。
4. 补充账单行编辑删除、优惠分摊、结算黄金样例和会员资产页面。
5. SQL Server镜像就绪后验证7个既有Migration、预约冲突、账单金额约束和并发结算。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -2 --oneline
sed -n '1,240p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
find backend/src frontend/src -type f | sort
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
