# 芋道单体后端上游

- 上游仓库：<https://github.com/YunaiV/ruoyi-vue-pro>
- 上游分支：`master-jdk17`
- 初始导入提交：`ec3f7cbf73e88514a70a6b59d365092ee470603d`
- 导入日期：2026-07-30
- 许可证：MIT，许可证原文保留在本目录 `LICENSE`
- 导入方式：Git subtree，前缀为 `backend/`

当前默认只构建芋道的 `system`、`infra` 和 `yudao-server` 模块。会员、流程、支付、报表、ERP、WMS 等完整源码已经保留，按照悦指间迁移顺序逐个启用，避免一次性引入无验收范围的菜单和依赖。

上游各数据库基线中的外部对象存储和短信渠道演示记录带有样例凭据，导入时已统一删除；SQL Server 基线仅保留无外部密钥的本地文件配置。MinIO、短信和云存储必须在部署环境中重新配置，禁止恢复或沿用上游演示值。

## 同步方式

项目仓库配置了只读用途的 `yudao-boot` remote。同步前先检查上游变更，再在独立提交中执行：

```bash
git fetch yudao-boot master-jdk17
git subtree pull --prefix=backend yudao-boot master-jdk17 --squash
```

同步后必须复核 `application-yuezhijian.yaml`、Flyway 增量迁移、自定义业务模块、SQL Server 兼容性和前后端接口，不得直接部署上游 `local` 演示 profile。
