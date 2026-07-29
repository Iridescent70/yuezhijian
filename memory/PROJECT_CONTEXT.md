# 稳定项目上下文

最后核对：2026-07-29。

## 项目目标

- 在2026-08-28前重构“悦·指间美甲管理系统”并完成门店管理系统V3优化。
- 两份合同清单总览139项，包含会员、预约、账单、结算、资产、提成、统计、移动端、到家服务和AI推荐。
- 老系统数据需要迁移到新系统，会员余额、积分、次卡、账单和支付必须完成对账。

## 需求和设计入口

- 项目范围：`docs/项目总计划.md`
- 两份合同清单：`docs/悦指间门店管理系统.md`、`docs/悦指间门店管理系统优化功能清单.md`
- 技术基线：`plan/技术实施计划.md`
- API：`plan/API接口.md`
- 页面和路由：`plan/页面ui.md`
- 数据库：`plan/数据库表.md`
- 当前已完成迭代：`ITER-00`至`ITER-03`；下一迭代为账单、开单和结算基础闭环。

## 已冻结技术决定

- 目录：后端统一在 `backend/`，PC前端统一在 `frontend/`；不得再增加 `server/`、`admin-web/` 等项目级嵌套目录。
- 后端：Java 21、Spring Boot 3.5、Spring Security、Spring Session JDBC、MyBatis、Flyway。
- PC前端：Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router。
- 数据库：SQL Server 2022；旧库只读，新库只允许Flyway修改。
- 架构：单仓库、前后端分离、模块化单体；首月不拆微服务。
- 金额：Java使用BigDecimal，数据库使用 `decimal(19,4)`，禁止float/double。
- 资产：储值、积分、次卡、库存、提成使用“当前余额/快照＋不可变流水”。
- 调账和冲销：生成反向流水，不覆盖原交易。
- 首个纵向样板：登录→当前用户→门店→角色权限→工作台。

## 旧源码情况

- 本地参考目录：`ref/legacy-source/CustomerServiceWebApp`，已加入 `.gitignore`。
- 已确认旧技术栈：Java 6、Struts、Spring、Hibernate、SQL Server、JSP、jQuery/DWZ。
- 旧包可用于JSP页面、`.do`入口和Hibernate映射分析，但压缩包不完整，缺少完整构建文件和部分运行依赖。
- 真实表结构、金额单位和历史数据只能以甲方数据库备份为准，不能仅凭Hibernate映射定稿。
- 齐总版和钇休版尚未放入当前工作区，暂时无法比较选主版本。

## 未确认范围

- 合同中的数据中心5项只有总览，没有逐项指标、公式、筛选和验收口径。
- AI输出范围、第三方支付/短信具体通道、甲方数据库备份和正式迁移停机窗口仍需确认。
