package com.yuezhijian.server.visit;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VisitMapper {
    String TASK_SELECT = """
            SELECT task.id, task.task_no AS taskNo, task.member_id AS memberId,
                   task.bill_id AS billId, bill.bill_no AS billNo, member.full_name AS customerName,
                   CONCAT('*******', RTRIM(member.mobile_last4)) AS maskedMobile,
                   task.store_id AS storeId, store.store_name AS storeName, task.due_at AS dueAt,
                   task.task_type AS taskType,
                   CASE WHEN task.status = 'PENDING'
                             AND DATEDIFF_BIG(millisecond, task.due_at, sysdatetime()) > 0
                        THEN 'OVERDUE' ELSE task.status END AS status,
                   CAST(CASE WHEN task.status = 'PENDING'
                                  AND DATEDIFF_BIG(millisecond, task.due_at, sysdatetime()) > 0
                             THEN 1 ELSE 0 END AS bit) AS overdue,
                   task.complaint_flag AS complaintFlag,
                   (SELECT COUNT(1) FROM dbo.vis_visit_participant p WHERE p.task_id = task.id) AS participantCount,
                   (SELECT COUNT(1) FROM dbo.vis_visit_participant p
                     WHERE p.task_id = task.id AND p.status = 'COMPLETED') AS completedCount,
                   task.conclusion, bill.settled_at AS settledAt, task.completed_at AS completedAt,
                   task.canceled_at AS canceledAt, task.cancel_reason AS cancelReason, task.created_at AS createdAt
            FROM dbo.vis_visit_task task
            JOIN dbo.mem_member member ON member.id = task.member_id
            JOIN dbo.trd_bill bill ON bill.id = task.bill_id
            JOIN dbo.org_store store ON store.id = task.store_id
            """;

    @Select("""
            <script>
            <bind name="pattern" value="query.keyword == null ? null : '%' + query.keyword + '%'" />
            """ + TASK_SELECT + """
            WHERE (#{query.storeId} IS NULL OR task.store_id = #{query.storeId})
              AND (#{query.employeeId} IS NULL OR EXISTS (
                    SELECT 1 FROM dbo.vis_visit_participant fp
                    WHERE fp.task_id = task.id AND fp.employee_id = #{query.employeeId}))
              <choose>
                <when test="query.status == 'OVERDUE'">
                  AND task.status = 'PENDING' AND task.due_at &lt; sysdatetime()
                </when>
                <when test="query.status == 'PENDING'">
                  AND task.status = 'PENDING' AND task.due_at &gt;= sysdatetime()
                </when>
                <when test="query.status != null">
                  AND task.status = #{query.status}
                </when>
              </choose>
              AND (#{query.dueDate} IS NULL OR (
                    task.due_at &gt;= #{query.dueDate} AND task.due_at &lt; DATEADD(day, 1, #{query.dueDate})))
              AND (#{query.keyword} IS NULL OR task.task_no LIKE #{pattern} OR bill.bill_no LIKE #{pattern}
                   OR member.full_name LIKE #{pattern} OR member.mobile_last4 LIKE #{pattern})
            ORDER BY CASE WHEN task.status = 'PENDING' THEN 0 ELSE 1 END, task.due_at, task.id
            </script>
            """)
    List<VisitTaskSummary> findTasks(@Param("query") VisitTaskQuery query);

    @Select(TASK_SELECT + " WHERE task.id = #{id}")
    VisitTaskSummary findTask(long id);

    @Select(TASK_SELECT + " WHERE task.bill_id = #{billId}")
    VisitTaskSummary findTaskByBill(long billId);

    @Select("""
            SELECT participant.id, participant.employee_id AS employeeId,
                   COALESCE(employee.name, participant.employee_name_snapshot, N'待分配') AS employeeName,
                   participant.service_summary AS serviceSummary, participant.status,
                   participant.completed_at AS completedAt
            FROM dbo.vis_visit_participant participant
            LEFT JOIN dbo.org_employee employee ON employee.id = participant.employee_id
            WHERE participant.task_id = #{taskId}
            ORDER BY participant.id
            """)
    List<VisitParticipantItem> findParticipants(long taskId);

    @Select("""
            SELECT record.id, record.participant_id AS participantId, record.employee_id AS employeeId,
                   employee.name AS employeeName, record.result_code AS resultCode,
                   record.satisfaction_score AS satisfactionScore, record.complaint_flag AS complaintFlag,
                   record.content, record.next_follow_at AS nextFollowAt, record.created_at AS createdAt,
                   record.created_by AS createdBy, creator.full_name AS createdByName
            FROM dbo.vis_visit_record record
            JOIN dbo.org_employee employee ON employee.id = record.employee_id
            JOIN dbo.iam_user creator ON creator.id = record.created_by
            WHERE record.task_id = #{taskId}
            ORDER BY record.created_at, record.id
            """)
    List<VisitRecordItem> findRecords(long taskId);

    @Insert("""
            INSERT INTO dbo.vis_visit_task (
                task_no, member_id, bill_id, store_id, due_at, task_type, status,
                complaint_flag, created_by, updated_by
            ) VALUES (
                #{task.taskNo}, #{task.memberId}, #{task.billId}, #{task.storeId}, #{task.dueAt},
                'AFTER_SALE', 'PENDING', 0, #{task.createdBy}, #{task.createdBy}
            )
            """)
    int insertTask(@Param("task") VisitTaskDraft task);

    @Insert("""
            INSERT INTO dbo.vis_visit_participant (
                task_id, employee_id, employee_name_snapshot, service_summary, status, created_by
            ) VALUES (
                #{taskId}, #{participant.employeeId}, #{participant.employeeName},
                #{participant.serviceSummary}, 'PENDING', #{operatorId}
            )
            """)
    int insertParticipant(
            @Param("taskId") long taskId,
            @Param("participant") VisitParticipantDraft participant,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.vis_visit_participant
            SET employee_id = #{employeeId}, employee_name_snapshot = #{employeeName}
            WHERE id = #{participantId} AND task_id = #{taskId}
              AND employee_id IS NULL AND status = 'PENDING'
            """)
    int claimParticipant(
            @Param("taskId") long taskId,
            @Param("participantId") long participantId,
            @Param("employeeId") long employeeId,
            @Param("employeeName") String employeeName);

    @Insert("""
            INSERT INTO dbo.vis_visit_record (
                task_id, participant_id, employee_id, result_code, satisfaction_score,
                complaint_flag, content, next_follow_at, created_by
            ) VALUES (
                #{taskId}, #{participantId}, #{employeeId}, #{resultCode}, #{satisfactionScore},
                #{complaintFlag}, #{content}, #{nextFollowAt}, #{createdBy}
            )
            """)
    int insertRecord(VisitRecordDraft draft);

    @Update("""
            UPDATE dbo.vis_visit_participant
            SET status = 'COMPLETED', completed_at = sysdatetime()
            WHERE id = #{participantId} AND task_id = #{taskId} AND status = 'PENDING'
            """)
    int completeParticipant(@Param("taskId") long taskId, @Param("participantId") long participantId);

    @Update("""
            UPDATE dbo.vis_visit_task
            SET due_at = COALESCE(#{draft.nextFollowAt}, due_at),
                complaint_flag = CASE WHEN #{draft.complaintFlag} = 1 THEN 1 ELSE complaint_flag END,
                updated_at = sysdatetime(), updated_by = #{draft.createdBy}
            WHERE id = #{draft.taskId} AND status = 'PENDING'
            """)
    int updateTaskFromRecord(@Param("draft") VisitRecordDraft draft);

    @Update("""
            UPDATE task
            SET status = 'COMPLETED', completed_at = sysdatetime(),
                updated_at = sysdatetime(), updated_by = #{operatorId}
            FROM dbo.vis_visit_task task
            WHERE task.id = #{taskId} AND task.status = 'PENDING'
              AND NOT EXISTS (
                  SELECT 1 FROM dbo.vis_visit_participant participant
                  WHERE participant.task_id = task.id AND participant.status = 'PENDING'
              )
            """)
    int autoCompleteTask(@Param("taskId") long taskId, @Param("operatorId") long operatorId);

    @Update("""
            UPDATE task
            SET conclusion = #{conclusion}, status = 'COMPLETED',
                completed_at = COALESCE(completed_at, sysdatetime()),
                updated_at = sysdatetime(), updated_by = #{operatorId}
            FROM dbo.vis_visit_task task
            WHERE task.id = #{taskId} AND task.status IN ('PENDING', 'COMPLETED')
              AND NOT EXISTS (
                  SELECT 1 FROM dbo.vis_visit_participant participant
                  WHERE participant.task_id = task.id AND participant.status = 'PENDING'
              )
            """)
    int completeTask(
            @Param("taskId") long taskId,
            @Param("conclusion") String conclusion,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.vis_visit_task
            SET status = 'CANCELLED', canceled_at = sysdatetime(), cancel_reason = #{reason},
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE bill_id = #{billId} AND status = 'PENDING'
            """)
    int cancelPendingByBill(
            @Param("billId") long billId,
            @Param("reason") String reason,
            @Param("operatorId") long operatorId);
}
