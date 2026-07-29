# 数据库 Migration 变更记录

本目录记录每次数据库结构和数据修复的人工说明，便于开发、测试、上线和审计查看。这里不存放实际执行 SQL；实际 SQL 必须放在：

```text
backend/src/main/resources/db/migration/
```

## 使用方式

每个 Flyway 版本对应一份同号说明：

```text
plan/migration/
├── README.md
├── V202607290900__init_common.md
├── V202607290910__init_iam_org.md
└── ...
```

说明文件模板：

```markdown
# VyyyyMMddHHmm 变更名称

| 项目 | 内容 |
| --- | --- |
| SQL 文件 | VyyyyMMddHHmm__description.sql |
| 日期 | yyyy-MM-dd |
| 需求/缺陷 | 功能编号、缺陷编号 |
| 影响 API | API-xxx |
| 影响页面 | UI-xxx |
| 影响表 | table_a、table_b |
| 风险等级 | 低/中/高 |

## 变更原因

## DDL/数据变化

## 兼容和数据回填

## 验证 SQL 与预期结果

## 部署步骤

## 失败恢复方式

## 各环境执行记录

| 环境 | 执行时间 | 执行人/流水线 | 耗时 | 结果 | 证据 |
| --- | --- | --- | --- | --- | --- |
```

## 规则

1. SQL 与说明文件必须在同一个 Pull Request 中提交。
2. SQL 一经共享环境执行不得修改；发现错误时增加下一版本修复。
3. 新增非空列必须说明旧数据如何回填；删除或缩窄字段必须先证明无引用并备份。
4. 大表索引、字段类型变更和数据更新必须在旧库数据量级上演练，并记录锁表和耗时。
5. 迁移执行前后都运行 `flyway validate`，并保存 `flyway_schema_history` 结果。
6. 数据库结构变化必须同步 `数据库表.md`、OpenAPI、测试数据工厂和迁移/回滚脚本。
7. 手工临时 SQL 不得成为最终状态；即使线上应急，也要立即补成 Flyway 版本并记录原因。

## 首版 Migration 批次

| 版本 | 内容 | 主要表 |
| --- | --- | --- |
| `V202607290900` | 公共任务、文件、参数、审计和幂等 | `sys_*` |
| `V202607290910` | 用户权限、组织、门店、员工 | `iam_*`、`org_*` |
| `V202607290920` | 产品、服务、次卡、支付和门店配置 | `cat_*`、`cfg_*` |
| `V202607290930` | 会员、档案、储值、积分和次卡资产 | `mem_*`、`ast_*` |
| `V202607290940` | 预约、账单、支付、调账和冲销 | `apt_*`、`trd_*` |
| `V202607290950` | 权益、库存和设备 | `ben_*`、`inv_*`、`eqp_*` |
| `V202607291000` | 回访、短信和通知 | `vis_*`、`mkt_*`、`ntf_*` |
| `V202607291010` | 提成、分润、目标和工资 | `comm_*`、`payroll_*` |
| `V202607291020` | 到家、AI 和第三方适配 | `home_*`、`ai_*`、`intg_*` |
| `V202607291030` | 历史数据迁移审计 | `migration_*`、`legacy_id_map` |
| `V202607291100` | 会员主档、会员卡、标签和资产汇总账户 | `mem_*`、`ast_*` |
| `V202607291110` | 服务项目、员工工位所需的预约前置主数据 | `cat_*`、`org_workstation` |
| `V202607291120` | 预约主档、项目快照、人员占用及状态历史 | `apt_*`、`sys_cancel_reason` |
| `V202607291130` | 账单、账单行、支付方式、试算及支付流水 | `trd_*`、`cat_payment_method*` |
| `V202607291140` | 储值/积分不可变流水、充值试算和充值确认 | `ast_balance_ledger`、`ast_point_ledger`、`ast_recharge_*` |
| `V202607291150` | 次卡类型、售卡订单、会员次卡余额和次数流水 | `cat_card_*`、`ast_card_sale_order`、`ast_member_card*` |
| `V202607291160` | 储值、积分、次卡组合结算和资产使用事实 | `trd_settlement_quote_asset`、`trd_bill_asset_usage` |
| `V202607291170` | 账单行软删除、整单优惠及逐行分摊 | `trd_bill_line`、`trd_bill_discount` |
| `V202607291180` | 整单冲销审批、支付退款事实及冲销权限 | `trd_reversal`、`trd_payment_refund` |
| `V202607291190` | 次卡换卡报价、补差支付和新旧卡关联 | `ast_card_exchange*`、`ast_member_card` |
| `V202607291200` | 次卡转赠、接收会员新卡和双方流水 | `ast_card_transfer`、`ast_member_card*` |
| `V202607291210` | 退卡试算、申请审批、退款事实和卡清零 | `ast_card_refund_*`、`ast_member_card*` |
| `V202607291220` | 代金券定义、发放绑定、结算核销和冲销返券 | `cat_voucher`、`ben_voucher_*`、`trd_*_asset*` |
| `V202607291230` | 版本化提成方案、服务计提事实和账单冲销负向流水 | `comm_plan*`、`comm_ledger` |
| `V202607291240` | 提成测算页菜单及排序 | `iam_menu` |
| `V202607291250` | 结算后回访、多技师记录、满意度和客诉 | `vis_visit_task`、`vis_visit_participant`、`vis_visit_record` |
| `V202607291260` | 服务反馈、负责人、处理状态和历史 | `vis_feedback`、`vis_feedback_action` |
| `V202607291270` | 回访时限参数、满意度规则、权限和菜单 | `sys_parameter`、`vis_satisfaction_rule` |
| `V202607291280` | 会员资料维护、状态历史和手工标签权限 | `mem_member_status_log`、`iam_permission` |
| `V202607291290` | 会员归属调整申请、审批、到期执行和菜单 | `mem_ownership_adjustment`、`iam_permission`、`iam_menu` |
| `V202607291300` | 会员顾问变更前后值和操作历史 | `mem_member_advisor_log` |
| `V202607291310` | 服务反馈处理时限参数、快照和查询索引 | `sys_parameter`、`vis_feedback` |
| `V202607291320` | 私有文件状态约束和通用业务附件关系 | `sys_file_object`、`sys_file_attachment` |
| `V202607291330` | 异步任务门店/过期信息、权限及下载中心菜单 | `sys_async_job`、`iam_permission`、`iam_menu` |
| `V202607291340` | 会员名单独立导出权限 | `iam_permission`、`iam_role_permission` |
| `V202607291350` | 异步任务租约、领取次数和调度索引 | `sys_async_job` |
| `V202607291360` | 结果文件清理时间和到期扫描索引 | `sys_async_job` |
| `V202607291370` | 服务项目独立导出权限 | `iam_permission`、`iam_role_permission` |
| `V202607291380` | 异步导入任务私有输入文件引用 | `sys_async_job`、`sys_file_object` |
| `V202607291390` | 产品主档、默认分类、权限和菜单 | `cat_product`、`cat_category`、`iam_*` |
| `V202607291400` | 产品资料独立导出权限 | `iam_permission`、`iam_role_permission` |
| `V202607291410` | 职务状态约束、维护权限和系统菜单 | `org_position`、`iam_permission`、`iam_role_permission`、`iam_menu` |
| `V202607291420` | 产品/服务分类与计量单位维护权限和菜单 | `iam_permission`、`iam_role_permission`、`iam_menu` |
| `V202607291430` | 操作日志查询权限和系统管理菜单 | `iam_permission`、`iam_role_permission`、`iam_menu` |
| `V202607291440` | 支付方式门店配置版本、权限和菜单 | `cat_payment_method_store`、`iam_permission`、`iam_role_permission`、`iam_menu` |
| `V202607291450` | 服务小区、门店范围、并发版本、权限和菜单 | `cfg_service_area`、`iam_permission`、`iam_role_permission`、`iam_menu` |
| `V202607291460` | 取消原因操作人、业务约束、账单原因、权限和菜单 | `sys_cancel_reason`、`iam_permission`、`iam_role_permission`、`iam_menu` |
| `R__reporting_views` | 可重复构建的统计视图 | `rpt_*` 视图/函数 |

表中未落地版本仍属于设计基线；实际脚本只能在当前最高版本之后追加，逐个建立同号说明并记录各环境执行结果，禁止为了补齐旧编号而开启Flyway乱序执行。
