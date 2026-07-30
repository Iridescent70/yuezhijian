package com.yuezhijian.server.banner;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BannerMapper {
    String BANNER_SELECT = """
            SELECT banner.id, banner.position_code AS positionCode, banner.title,
                   banner.image_file_id AS imageFileId, image.original_name AS imageName,
                   image.content_type AS imageContentType, banner.link_type AS linkType,
                   banner.link_value AS linkValue, banner.sort_no AS sortNo,
                   banner.valid_from AS validFrom, banner.valid_to AS validTo, banner.status,
                   banner.updated_at AS updatedAt, banner.updated_by AS updatedBy,
                   COALESCE(operator.full_name, operator.username, N'系统任务') AS updatedByName,
                   CONVERT(varchar(18), CAST(banner.row_version AS varbinary(8)), 1) AS version
            FROM dbo.cfg_banner banner
            JOIN dbo.sys_file_object image ON image.id = banner.image_file_id AND image.status = 'ACTIVE'
            LEFT JOIN dbo.iam_user operator ON operator.id = banner.updated_by
            """;

    @Select("""
            <script>
            """ + BANNER_SELECT + """
            WHERE 1 = 1
            <if test="positionCode != null">AND banner.position_code = #{positionCode}</if>
            <if test="keyword != null">AND banner.title LIKE CONCAT('%', #{keyword}, '%')</if>
            <if test="status != null">AND banner.status = #{status}</if>
            ORDER BY banner.position_code, banner.sort_no, banner.id
            </script>
            """)
    List<Banner> findAll(
            @Param("positionCode") String positionCode,
            @Param("keyword") String keyword,
            @Param("status") String status);

    @Select(BANNER_SELECT + """
             WHERE banner.position_code = #{positionCode} AND banner.status = 'ACTIVE'
               AND (banner.valid_from IS NULL OR banner.valid_from <= #{now})
               AND (banner.valid_to IS NULL OR banner.valid_to >= #{now})
             ORDER BY banner.sort_no, banner.id
            """)
    List<Banner> findActive(@Param("positionCode") String positionCode, @Param("now") LocalDateTime now);

    @Select(BANNER_SELECT + " WHERE banner.id = #{id}")
    Banner find(long id);

    @Select(value = """
            INSERT INTO dbo.cfg_banner (
                position_code, title, image_file_id, link_type, link_value, sort_no,
                valid_from, valid_to, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{positionCode}, #{title}, #{imageFileId}, #{linkType}, #{linkValue}, #{sortNo},
                #{validFrom}, #{validTo}, #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insert(NewBanner banner);

    @Update("""
            UPDATE dbo.cfg_banner
            SET position_code = #{positionCode}, title = #{title}, link_type = #{linkType},
                link_value = #{linkValue}, sort_no = #{sortNo}, valid_from = #{validFrom},
                valid_to = #{validTo}, status = #{status}, updated_at = sysdatetime(),
                updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int update(BannerUpdate update);

    @Update("""
            UPDATE dbo.cfg_banner
            SET image_file_id = #{imageFileId}, updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int replaceImage(BannerImageUpdate update);
}
