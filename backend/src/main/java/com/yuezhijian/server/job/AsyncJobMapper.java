package com.yuezhijian.server.job;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AsyncJobMapper {
    String ITEM_SELECT = """
            SELECT job.id, job.job_no AS jobNo, COALESCE(job.job_name, job.job_type) AS jobName,
                   job.job_type AS jobType, job.status, job.progress,
                   job.success_count AS successCount, job.failure_count AS failureCount,
                   job.result_file_id AS resultFileId, result_file.original_name AS resultFileName,
                   job.error_file_id AS errorFileId, error_file.original_name AS errorFileName,
                   job.error_message AS errorMessage, job.started_at AS startedAt,
                   job.finished_at AS finishedAt, job.expires_at AS expiresAt,
                   job.created_at AS createdAt, job.created_by AS createdBy,
                   creator.full_name AS createdByName
            FROM dbo.sys_async_job job
            LEFT JOIN dbo.sys_file_object result_file ON result_file.id = job.result_file_id
            LEFT JOIN dbo.sys_file_object error_file ON error_file.id = job.error_file_id
            JOIN dbo.iam_user creator ON creator.id = job.created_by
            """;

    @Select("""
            SELECT COUNT(1) FROM dbo.sys_async_job
            WHERE created_by = #{createdBy} AND status IN ('PENDING', 'RUNNING')
            """)
    int countActive(long createdBy);

    @Select(value = """
            INSERT INTO dbo.sys_async_job (
                job_no, job_name, job_type, request_json, store_id, expires_at,
                status, progress, success_count, failure_count, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{jobNo}, #{jobName}, #{jobType}, #{requestJson}, #{storeId}, #{expiresAt},
                'PENDING', 0, 0, 0, #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insert(AsyncJobDraft draft);

    @Select("""
            <script>
            """ + ITEM_SELECT + """
            WHERE job.created_by = #{query.createdBy}
              <if test="query.jobType != null">AND job.job_type = #{query.jobType}</if>
              <if test="query.status != null">AND job.status = #{query.status}</if>
            ORDER BY job.created_at DESC, job.id DESC
            OFFSET #{query.offset} ROWS FETCH NEXT #{query.size} ROWS ONLY
            </script>
            """)
    List<AsyncJobItem> findJobs(@Param("query") AsyncJobQuery query);

    @Select("""
            <script>
            SELECT COUNT(1) FROM dbo.sys_async_job job
            WHERE job.created_by = #{query.createdBy}
              <if test="query.jobType != null">AND job.job_type = #{query.jobType}</if>
              <if test="query.status != null">AND job.status = #{query.status}</if>
            </script>
            """)
    long countJobs(@Param("query") AsyncJobQuery query);

    @Select(ITEM_SELECT + " WHERE job.id = #{id} AND job.created_by = #{createdBy}")
    AsyncJobItem findOwned(@Param("id") long id, @Param("createdBy") long createdBy);

    @Select(value = """
            ;WITH next_job AS (
                SELECT TOP (1) *
                FROM dbo.sys_async_job WITH (UPDLOCK, READPAST, ROWLOCK)
                WHERE attempt_count < #{maxAttempts}
                  AND (status = 'PENDING' OR (
                    status = 'RUNNING' AND (lease_expires_at IS NULL OR lease_expires_at <= sysdatetime())
                  ))
                ORDER BY created_at, id
            )
            UPDATE next_job
            SET status = 'RUNNING', progress = 1, started_at = sysdatetime(),
                lease_token = #{leaseToken}, lease_expires_at = #{leaseExpiresAt},
                attempt_count = attempt_count + 1, error_message = NULL,
                finished_at = NULL, updated_at = sysdatetime(), updated_by = created_by
            OUTPUT INSERTED.id, INSERTED.job_no AS jobNo, INSERTED.job_type AS jobType,
                   INSERTED.request_json AS requestJson, INSERTED.store_id AS storeId,
                   INSERTED.created_by AS createdBy, INSERTED.lease_token AS leaseToken,
                   INSERTED.attempt_count AS attemptCount;
            """, affectData = true)
    AsyncJobTask claimNext(
            @Param("leaseToken") String leaseToken,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
            @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE dbo.sys_async_job
            SET lease_expires_at = #{leaseExpiresAt}, updated_at = sysdatetime()
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{leaseToken}
            """)
    int renewLease(
            @Param("id") long id,
            @Param("leaseToken") String leaseToken,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("""
            UPDATE dbo.sys_async_job
            SET status = #{status}, progress = 100,
                success_count = #{successCount}, failure_count = #{failureCount},
                result_file_id = #{resultFileId}, finished_at = sysdatetime(),
                lease_token = NULL, lease_expires_at = NULL,
                updated_at = sysdatetime(), updated_by = created_by
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{leaseToken}
            """)
    int complete(
            @Param("id") long id,
            @Param("leaseToken") String leaseToken,
            @Param("status") String status,
            @Param("successCount") int successCount,
            @Param("failureCount") int failureCount,
            @Param("resultFileId") long resultFileId);

    @Update("""
            UPDATE dbo.sys_async_job
            SET status = 'FAILED', progress = 100, failure_count = CASE WHEN failure_count = 0 THEN 1 ELSE failure_count END,
                error_message = #{errorMessage}, finished_at = sysdatetime(),
                lease_token = NULL, lease_expires_at = NULL,
                updated_at = sysdatetime(), updated_by = created_by
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{leaseToken}
            """)
    int fail(
            @Param("id") long id,
            @Param("leaseToken") String leaseToken,
            @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE dbo.sys_async_job
            SET status = 'FAILED', progress = 100,
                failure_count = CASE WHEN failure_count = 0 THEN 1 ELSE failure_count END,
                error_message = N'任务执行节点失联，已达到最大重试次数',
                finished_at = sysdatetime(), lease_token = NULL, lease_expires_at = NULL,
                updated_at = sysdatetime(), updated_by = created_by
            WHERE status = 'RUNNING'
              AND (lease_expires_at IS NULL OR lease_expires_at <= sysdatetime())
              AND attempt_count >= #{maxAttempts}
            """)
    int failExhausted(@Param("maxAttempts") int maxAttempts);

    @Select("""
            SELECT TOP (#{limit}) id AS jobId, result_file_id AS fileId
            FROM dbo.sys_async_job WITH (READPAST)
            WHERE status IN ('SUCCEEDED', 'PARTIAL')
              AND expires_at <= sysdatetime()
              AND result_file_id IS NOT NULL AND result_purged_at IS NULL
            ORDER BY expires_at, id
            """)
    List<ExpiredJobResult> findExpiredResults(@Param("limit") int limit);

    @Update("""
            UPDATE dbo.sys_async_job
            SET result_purged_at = sysdatetime(), updated_at = sysdatetime(), updated_by = created_by
            WHERE id = #{jobId} AND result_file_id = #{fileId}
              AND status IN ('SUCCEEDED', 'PARTIAL') AND expires_at <= sysdatetime()
              AND result_purged_at IS NULL
            """)
    int markResultPurged(@Param("jobId") long jobId, @Param("fileId") long fileId);

    @Update("""
            UPDATE dbo.sys_async_job
            SET status = 'CANCELLED', finished_at = sysdatetime(),
                updated_at = sysdatetime(), updated_by = #{createdBy}
            WHERE id = #{id} AND created_by = #{createdBy} AND status = 'PENDING'
            """)
    int cancel(@Param("id") long id, @Param("createdBy") long createdBy);
}
