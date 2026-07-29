package com.yuezhijian.server.file;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileObjectMapper {
    String FILE_SELECT = """
            SELECT 0 AS attachmentId, id AS fileId, object_key AS objectKey,
                   original_name AS originalName, content_type AS contentType,
                   size_bytes AS sizeBytes, sha256, purpose, NULL AS category
            FROM dbo.sys_file_object
            WHERE status = 'ACTIVE'
            """;

    String ACTIVE_SELECT = """
            SELECT attachment.id AS attachmentId, file_object.id AS fileId,
                   file_object.object_key AS objectKey, file_object.original_name AS originalName,
                   file_object.content_type AS contentType, file_object.size_bytes AS sizeBytes,
                   file_object.sha256, file_object.purpose, attachment.category
            FROM dbo.sys_file_attachment attachment
            JOIN dbo.sys_file_object file_object ON file_object.id = attachment.file_id
            WHERE attachment.business_type = #{businessType}
              AND attachment.business_id = #{businessId}
              AND attachment.removed_at IS NULL AND file_object.status = 'ACTIVE'
            """;

    @Select("""
            SELECT COUNT(1)
            FROM dbo.sys_file_attachment attachment
            JOIN dbo.sys_file_object file_object ON file_object.id = attachment.file_id
            WHERE attachment.business_type = #{businessType} AND attachment.business_id = #{businessId}
              AND attachment.removed_at IS NULL AND file_object.status = 'ACTIVE'
            """)
    int countActive(@Param("businessType") String businessType, @Param("businessId") long businessId);

    @Select("""
            SELECT attachment.id, file_object.id AS fileId,
                   file_object.original_name AS originalName, file_object.content_type AS contentType,
                   file_object.size_bytes AS sizeBytes, file_object.sha256, file_object.purpose,
                   attachment.category, attachment.created_at AS createdAt,
                   attachment.created_by AS createdBy, creator.full_name AS createdByName
            FROM dbo.sys_file_attachment attachment
            JOIN dbo.sys_file_object file_object ON file_object.id = attachment.file_id
            JOIN dbo.iam_user creator ON creator.id = attachment.created_by
            WHERE attachment.business_type = #{businessType} AND attachment.business_id = #{businessId}
              AND attachment.removed_at IS NULL AND file_object.status = 'ACTIVE'
            ORDER BY attachment.created_at, attachment.id
            """)
    List<BusinessAttachmentItem> findAttachments(
            @Param("businessType") String businessType, @Param("businessId") long businessId);

    @Select(ACTIVE_SELECT + " AND attachment.id = #{attachmentId}")
    StoredFileObject findActive(
            @Param("businessType") String businessType,
            @Param("businessId") long businessId,
            @Param("attachmentId") long attachmentId);

    @Select(FILE_SELECT + " AND id = #{fileId}")
    StoredFileObject findActiveFile(long fileId);

    @Select("""
            SELECT id, original_name AS originalName, content_type AS contentType,
                   size_bytes AS sizeBytes, sha256, purpose, created_at AS createdAt,
                   owner_user_id AS ownerUserId
            FROM dbo.sys_file_object
            WHERE id = #{fileId} AND status = 'ACTIVE'
            """)
    FileObjectItem findFileItem(long fileId);

    @Select(value = """
            INSERT INTO dbo.sys_file_object (
                object_key, original_name, content_type, size_bytes, sha256, purpose,
                owner_user_id, access_level, status, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{objectKey}, #{originalName}, #{contentType}, #{sizeBytes}, #{sha256}, #{purpose},
                #{ownerUserId}, 'STORE', 'ACTIVE', #{ownerUserId}, #{ownerUserId}
            )
            """, affectData = true)
    long insertFileObject(FileObjectDraft file);

    @Select(value = """
            INSERT INTO dbo.sys_file_attachment (
                file_id, business_type, business_id, store_id, category, created_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{fileId}, #{attachment.businessType}, #{attachment.businessId},
                #{attachment.storeId}, #{attachment.category}, #{attachment.operatorId}
            )
            """, affectData = true)
    long insertAttachment(@Param("fileId") long fileId, @Param("attachment") AttachmentDraft attachment);

    @Update("""
            UPDATE dbo.sys_file_attachment
            SET removed_at = sysdatetime(), removed_by = #{operatorId}
            WHERE id = #{attachmentId} AND business_type = #{businessType} AND business_id = #{businessId}
              AND removed_at IS NULL
            """)
    int softDeleteAttachment(
            @Param("businessType") String businessType,
            @Param("businessId") long businessId,
            @Param("attachmentId") long attachmentId,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.sys_file_object
            SET status = 'DELETED', updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = (SELECT file_id FROM dbo.sys_file_attachment WHERE id = #{attachmentId})
              AND status = 'ACTIVE'
            """)
    int softDeleteFile(@Param("attachmentId") long attachmentId, @Param("operatorId") long operatorId);
}
