# V202607291390 产品基础资料

- SQL：`backend/src/main/resources/db/migration/V202607291390__init_product_catalog.sql`
- 新表：`cat_product`，包含产品编号、名称、分类、单位、条码、成本、售价、库存属性、状态和`rowversion`。
- 索引：产品编号唯一；非空条码过滤唯一；分类、状态和ID组合查询索引。
- 基础数据：新增`PRODUCT/RETAIL_PRODUCT`默认分类，不修改既有服务分类和单位。
- 权限菜单：新增`catalog:product:view/manage`及`/app/catalog/products`菜单，总部管理员默认授权。
- 回滚：上线前可删除菜单、角色权限、权限、产品分类、索引和产品表；产生业务引用后禁止直接删除表。
