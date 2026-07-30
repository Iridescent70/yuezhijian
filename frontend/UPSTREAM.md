# 芋道前端上游

- 上游仓库：<https://github.com/yudaocode/yudao-ui-admin-vue3>
- 上游分支：`master`
- 初始导入提交：`94459770d622303802c465510b1124ab794a6338`
- 导入日期：2026-07-30
- 许可证：MIT，许可证原文保留在本目录 `LICENSE`
- 导入方式：Git subtree，前缀为 `frontend/`

## 同步方式

项目仓库配置了只读用途的 `yudao-ui` remote。同步前必须先检查上游变更，再在独立提交中执行：

```bash
git fetch yudao-ui master
git subtree pull --prefix=frontend yudao-ui master --squash
```

同步后必须重新验证悦指间品牌配置、业务页面和与同版本芋道单体后端的 Token、动态菜单、字典、文件及 WebSocket 协议，不能把接口地址切换到芋道演示服务。
