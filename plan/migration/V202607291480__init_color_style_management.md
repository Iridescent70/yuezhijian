# V202607291480 线上试色管理

## 变更内容

- 新建`cat_color_style_category`，保存父分类、不可变分类编码、名称、分类图片、排序、状态和`rowversion`。
- 新建`cat_color_style`，保存不可变色号、名称、说明、排序、状态和`rowversion`。
- 新建`cat_color_style_category_assignment`，用组合主键保存色号与分类的多对多关系。
- 新建`cat_color_style_asset`，每张素材独立关联`sys_file_object`，支持排序、启停和并发更新。
- 新增`system:color-style:view/manage`权限和`/app/system/color-styles`菜单；操作日志菜单顺延。

## 数据和回滚

- 当前Migration只建新表，不自动搬运旧库文件名；历史分类、色号、关系和图片需在取得甲方备份后通过独立迁移批次导入并核对数量、编码及文件摘要。
- Flyway脚本上线后不回删表；回退应用时保留数据并隐藏菜单。如结构需修正，只能追加更高版本Migration。
- 图片对象均保存在私有存储，数据库不保存公开URL或对象键。

## 验证

- 核对4张表、外键、唯一约束、状态/排序检查、`rowversion`和查询索引。
- 核对总部管理员2项权限和菜单路由。
- 执行分类循环、重复编码、多分类、素材上传、伪装文件、并发冲突和审计流程测试。
