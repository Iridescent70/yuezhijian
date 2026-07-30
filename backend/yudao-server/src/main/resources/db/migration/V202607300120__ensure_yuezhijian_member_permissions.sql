DECLARE @RootMenuId bigint;
DECLARE @MemberMenuId bigint;

SELECT @RootMenuId = id
FROM dbo.system_menu
WHERE path = N'/yuezhijian' AND type = 1 AND deleted = 0;

SELECT @MemberMenuId = id
FROM dbo.system_menu
WHERE parent_id = @RootMenuId AND path = N'member' AND type = 2 AND deleted = 0;

IF @MemberMenuId IS NULL
    THROW 51000, N'悦指间会员主档菜单不存在，无法创建按钮权限', 1;

IF NOT EXISTS (
    SELECT 1 FROM dbo.system_menu
    WHERE permission = N'yuezhijian:member:query' AND deleted = 0
)
BEGIN
    INSERT INTO dbo.system_menu
        (name, permission, type, sort, parent_id, path, icon, component, component_name,
         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES
        (N'悦指间会员查询', N'yuezhijian:member:query', 3, 90, @MemberMenuId,
         N'', N'', N'', N'', 0, '1', '1', '1', N'system', CURRENT_TIMESTAMP,
         N'system', CURRENT_TIMESTAMP, 0);
END;

IF NOT EXISTS (
    SELECT 1 FROM dbo.system_menu
    WHERE permission = N'yuezhijian:member:create' AND deleted = 0
)
BEGIN
    INSERT INTO dbo.system_menu
        (name, permission, type, sort, parent_id, path, icon, component, component_name,
         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES
        (N'悦指间会员建档', N'yuezhijian:member:create', 3, 91, @MemberMenuId,
         N'', N'', N'', N'', 0, '1', '1', '1', N'system', CURRENT_TIMESTAMP,
         N'system', CURRENT_TIMESTAMP, 0);
END;

UPDATE dbo.system_menu
SET parent_id = @MemberMenuId,
    updater = N'system',
    update_time = CURRENT_TIMESTAMP
WHERE permission IN (N'yuezhijian:member:query', N'yuezhijian:member:create')
  AND deleted = 0
  AND parent_id <> @MemberMenuId;
