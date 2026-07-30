DECLARE @RootMenuId bigint;
DECLARE @StoreMenuId bigint;
DECLARE @EmployeeMenuId bigint;
DECLARE @MemberMenuId bigint;

SELECT @RootMenuId = id FROM dbo.system_menu
WHERE path = N'/yuezhijian' AND type = 1 AND deleted = 0;

IF @RootMenuId IS NULL
BEGIN
    INSERT INTO dbo.system_menu
        (name, permission, type, sort, parent_id, path, icon, component, component_name,
         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES
        (N'悦指间业务', N'', 1, 15, 0, N'/yuezhijian', N'ep:shop', NULL, NULL,
         0, '1', '1', '1', N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0);
    SET @RootMenuId = SCOPE_IDENTITY();
END;

SELECT @StoreMenuId = id FROM dbo.system_menu
WHERE parent_id = @RootMenuId AND path = N'store' AND deleted = 0;
IF @StoreMenuId IS NULL
BEGIN
    INSERT INTO dbo.system_menu
        (name, permission, type, sort, parent_id, path, icon, component, component_name,
         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES
        (N'门店档案', N'', 2, 1, @RootMenuId, N'store', N'ep:office-building',
         N'yuezhijian/store/index', N'YuezhijianStore', 0, '1', '1', '1',
         N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0);
    SET @StoreMenuId = SCOPE_IDENTITY();
END;

SELECT @EmployeeMenuId = id FROM dbo.system_menu
WHERE parent_id = @RootMenuId AND path = N'employee' AND deleted = 0;
IF @EmployeeMenuId IS NULL
BEGIN
    INSERT INTO dbo.system_menu
        (name, permission, type, sort, parent_id, path, icon, component, component_name,
         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES
        (N'员工档案', N'', 2, 2, @RootMenuId, N'employee', N'ep:user',
         N'yuezhijian/employee/index', N'YuezhijianEmployee', 0, '1', '1', '1',
         N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0);
    SET @EmployeeMenuId = SCOPE_IDENTITY();
END;

SELECT @MemberMenuId = id FROM dbo.system_menu
WHERE parent_id = @RootMenuId AND path = N'member' AND deleted = 0;
IF @MemberMenuId IS NULL
BEGIN
    INSERT INTO dbo.system_menu
        (name, permission, type, sort, parent_id, path, icon, component, component_name,
         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES
        (N'会员主档', N'', 2, 3, @RootMenuId, N'member', N'ep:user-filled',
         N'yuezhijian/member/index', N'YuezhijianMember', 0, '1', '1', '1',
         N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0);
    SET @MemberMenuId = SCOPE_IDENTITY();
END;

UPDATE dbo.system_menu SET parent_id = @StoreMenuId, update_time = CURRENT_TIMESTAMP, updater = N'system'
WHERE permission IN (N'yuezhijian:store:query', N'yuezhijian:store:update') AND deleted = 0;

UPDATE dbo.system_menu SET parent_id = @EmployeeMenuId, update_time = CURRENT_TIMESTAMP, updater = N'system'
WHERE permission IN (N'yuezhijian:employee:query', N'yuezhijian:employee:update') AND deleted = 0;

UPDATE dbo.system_menu SET parent_id = @MemberMenuId, update_time = CURRENT_TIMESTAMP, updater = N'system'
WHERE permission IN (N'yuezhijian:member:query', N'yuezhijian:member:create') AND deleted = 0;
