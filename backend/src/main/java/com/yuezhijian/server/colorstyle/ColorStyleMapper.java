package com.yuezhijian.server.colorstyle;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ColorStyleMapper {
    String CATEGORY_SELECT = """
            SELECT category.id, category.parent_id AS parentId, category.category_code AS code,
                   category.category_name AS name, category.image_file_id AS imageFileId,
                   image.original_name AS imageName, image.content_type AS imageContentType,
                   category.sort_no AS sortNo, category.status, category.updated_at AS updatedAt,
                   category.updated_by AS updatedBy,
                   COALESCE(operator.full_name, operator.username, N'系统任务') AS updatedByName,
                   CONVERT(varchar(18), category.row_version, 1) AS version
            FROM dbo.cat_color_style_category category
            LEFT JOIN dbo.sys_file_object image
              ON image.id = category.image_file_id AND image.status = 'ACTIVE'
            LEFT JOIN dbo.iam_user operator ON operator.id = category.updated_by
            """;

    String STYLE_SELECT = """
            SELECT style.id, style.color_code AS code, style.color_name AS name,
                   style.description, style.sort_no AS sortNo, style.status,
                   style.updated_at AS updatedAt, style.updated_by AS updatedBy,
                   COALESCE(operator.full_name, operator.username, N'系统任务') AS updatedByName,
                   CONVERT(varchar(18), style.row_version, 1) AS version
            FROM dbo.cat_color_style style
            LEFT JOIN dbo.iam_user operator ON operator.id = style.updated_by
            """;

    String ASSET_SELECT = """
            SELECT asset.id, asset.color_style_id AS colorStyleId, asset.file_id AS fileId,
                   file_object.original_name AS fileName, file_object.content_type AS contentType,
                   asset.sort_no AS sortNo, asset.status, asset.updated_at AS updatedAt,
                   CONVERT(varchar(18), asset.row_version, 1) AS version
            FROM dbo.cat_color_style_asset asset
            JOIN dbo.sys_file_object file_object
              ON file_object.id = asset.file_id AND file_object.status = 'ACTIVE'
            """;

    @Select("""
            <script>
            """ + CATEGORY_SELECT + """
            WHERE 1 = 1
            <if test="keyword != null">
              AND (category.category_code LIKE CONCAT('%', #{keyword}, '%')
                   OR category.category_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND category.status = #{status}</if>
            ORDER BY category.sort_no, category.id
            </script>
            """)
    List<ColorStyleCategory> findCategories(
            @Param("keyword") String keyword, @Param("status") String status);

    @Select(CATEGORY_SELECT + " WHERE category.id = #{id}")
    ColorStyleCategory findCategory(long id);

    @Select(value = """
            INSERT INTO dbo.cat_color_style_category (
                parent_id, category_code, category_name, sort_no, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (#{parentId}, #{code}, #{name}, #{sortNo}, #{operatorId}, #{operatorId})
            """, affectData = true)
    long insertCategory(NewColorStyleCategory category);

    @Update("""
            UPDATE dbo.cat_color_style_category
            SET parent_id = #{parentId}, category_name = #{name}, sort_no = #{sortNo},
                status = #{status}, updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updateCategory(ColorStyleCategoryUpdate update);

    @Update("""
            UPDATE dbo.cat_color_style_category
            SET image_file_id = #{imageFileId}, updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int replaceCategoryImage(ColorStyleCategoryImageUpdate update);

    @Select("""
            SELECT CASE WHEN EXISTS (
                SELECT 1
                FROM dbo.cat_color_style_category_assignment assignment
                JOIN dbo.cat_color_style style ON style.id = assignment.color_style_id
                WHERE assignment.category_id = #{categoryId} AND style.status = 'ACTIVE'
            ) THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END
            """)
    boolean hasActiveStyleInCategory(long categoryId);

    @Select("""
            <script>
            """ + STYLE_SELECT + """
            WHERE 1 = 1
            <if test="categoryId != null">
              AND EXISTS (
                SELECT 1 FROM dbo.cat_color_style_category_assignment assignment
                WHERE assignment.color_style_id = style.id AND assignment.category_id = #{categoryId}
              )
            </if>
            <if test="keyword != null">
              AND (style.color_code LIKE CONCAT('%', #{keyword}, '%')
                   OR style.color_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND style.status = #{status}</if>
            ORDER BY style.sort_no, style.id
            OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY
            </script>
            """)
    List<ColorStyleRow> findStyles(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM dbo.cat_color_style style
            WHERE 1 = 1
            <if test="categoryId != null">
              AND EXISTS (
                SELECT 1 FROM dbo.cat_color_style_category_assignment assignment
                WHERE assignment.color_style_id = style.id AND assignment.category_id = #{categoryId}
              )
            </if>
            <if test="keyword != null">
              AND (style.color_code LIKE CONCAT('%', #{keyword}, '%')
                   OR style.color_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND style.status = #{status}</if>
            </script>
            """)
    long countStyles(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("status") String status);

    @Select(STYLE_SELECT + " WHERE style.id = #{id}")
    ColorStyleRow findStyle(long id);

    @Select("""
            SELECT category_id
            FROM dbo.cat_color_style_category_assignment
            WHERE color_style_id = #{styleId}
            ORDER BY category_id
            """)
    List<Long> findCategoryIds(long styleId);

    @Select(ASSET_SELECT + """
             WHERE asset.color_style_id = #{styleId}
             ORDER BY asset.sort_no, asset.id
            """)
    List<ColorStyleAsset> findAssets(long styleId);

    @Select(ASSET_SELECT + " WHERE asset.color_style_id = #{styleId} AND asset.id = #{assetId}")
    ColorStyleAsset findAsset(@Param("styleId") long styleId, @Param("assetId") long assetId);

    @Select("""
            SELECT COUNT(*)
            FROM dbo.cat_color_style_asset WITH (UPDLOCK, HOLDLOCK)
            WHERE color_style_id = #{styleId} AND status = 'ACTIVE'
            """)
    int activeAssetCountForUpdate(long styleId);

    @Select(value = """
            INSERT INTO dbo.cat_color_style (
                color_code, color_name, description, sort_no, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (#{code}, #{name}, #{description}, #{sortNo}, #{operatorId}, #{operatorId})
            """, affectData = true)
    long insertStyle(NewColorStyle style);

    @Update("""
            UPDATE dbo.cat_color_style
            SET color_name = #{name}, description = #{description}, sort_no = #{sortNo},
                status = #{status}, updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updateStyle(ColorStyleUpdate update);

    @Delete("DELETE FROM dbo.cat_color_style_category_assignment WHERE color_style_id = #{styleId}")
    int deleteAssignments(long styleId);

    @Insert("""
            INSERT INTO dbo.cat_color_style_category_assignment (
                category_id, color_style_id, created_by
            ) VALUES (#{categoryId}, #{styleId}, #{operatorId})
            """)
    int insertAssignment(
            @Param("styleId") long styleId,
            @Param("categoryId") long categoryId,
            @Param("operatorId") long operatorId);

    @Select(value = """
            INSERT INTO dbo.cat_color_style_asset (
                color_style_id, file_id, sort_no, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (#{colorStyleId}, #{fileId}, #{sortNo}, #{operatorId}, #{operatorId})
            """, affectData = true)
    long insertAsset(NewColorStyleAsset asset);

    @Update("""
            UPDATE dbo.cat_color_style_asset
            SET sort_no = #{sortNo}, status = #{status},
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND color_style_id = #{colorStyleId}
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updateAsset(ColorStyleAssetUpdate update);
}

record ColorStyleRow(
        long id,
        String code,
        String name,
        String description,
        int sortNo,
        String status,
        LocalDateTime updatedAt,
        Long updatedBy,
        String updatedByName,
        String version) {
}
