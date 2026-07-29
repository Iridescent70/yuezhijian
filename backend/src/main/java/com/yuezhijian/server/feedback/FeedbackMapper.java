package com.yuezhijian.server.feedback;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FeedbackMapper {
    String SUMMARY_SELECT = """
            SELECT feedback.id, feedback.feedback_no AS feedbackNo,
                   feedback.visit_task_id AS visitTaskId, feedback.visit_record_id AS visitRecordId,
                   feedback.member_id AS memberId, member.full_name AS memberName,
                   CONCAT('*******', RTRIM(member.mobile_last4)) AS maskedMobile,
                   feedback.bill_id AS billId, bill.bill_no AS billNo,
                   feedback.store_id AS storeId, store.store_name AS storeName,
                   feedback.channel, feedback.score, feedback.content,
                   feedback.complaint_type AS complaintType, feedback.status,
                   feedback.handler_id AS handlerId, handler.name AS handlerName,
                   feedback.handle_result AS handleResult, feedback.handled_at AS handledAt,
                   feedback.resolved_at AS resolvedAt, feedback.closed_at AS closedAt,
                   feedback.due_hours AS dueHours, feedback.due_at AS dueAt,
                   CAST(CASE WHEN feedback.status IN ('OPEN', 'PROCESSING')
                                  AND sysdatetime() > feedback.due_at THEN 1 ELSE 0 END AS bit) AS overdue,
                   CASE WHEN feedback.status IN ('OPEN', 'PROCESSING') AND sysdatetime() > feedback.due_at
                        THEN DATEDIFF(MINUTE, feedback.due_at, sysdatetime()) ELSE 0 END AS overdueMinutes,
                   (SELECT COUNT(1) FROM dbo.vis_feedback_action action
                     WHERE action.feedback_id = feedback.id) AS actionCount,
                   feedback.created_at AS createdAt, feedback.updated_at AS updatedAt
            FROM dbo.vis_feedback feedback
            JOIN dbo.mem_member member ON member.id = feedback.member_id
            JOIN dbo.trd_bill bill ON bill.id = feedback.bill_id
            JOIN dbo.org_store store ON store.id = feedback.store_id
            LEFT JOIN dbo.org_employee handler ON handler.id = feedback.handler_id
            """;

    @Select("""
            <script>
            <bind name="pattern" value="query.keyword == null ? null : '%' + query.keyword + '%'" />
            """ + SUMMARY_SELECT + """
            WHERE (#{query.storeId} IS NULL OR feedback.store_id = #{query.storeId})
              AND (#{query.handlerId} IS NULL OR feedback.handler_id = #{query.handlerId})
              AND (#{query.score} IS NULL OR feedback.score = #{query.score})
              AND (#{query.status} IS NULL OR feedback.status = #{query.status})
              AND (#{query.overdue} IS NULL
                   OR (#{query.overdue} = 1 AND feedback.status IN ('OPEN', 'PROCESSING')
                       AND sysdatetime() > feedback.due_at)
                   OR (#{query.overdue} = 0 AND NOT (
                       feedback.status IN ('OPEN', 'PROCESSING') AND sysdatetime() > feedback.due_at)))
              AND (#{query.keyword} IS NULL OR feedback.feedback_no LIKE #{pattern}
                   OR bill.bill_no LIKE #{pattern} OR member.full_name LIKE #{pattern}
                   OR member.mobile_last4 LIKE #{pattern})
            ORDER BY CASE WHEN feedback.status IN ('OPEN', 'PROCESSING')
                                AND sysdatetime() > feedback.due_at THEN 0 ELSE 1 END,
                     CASE feedback.status
                         WHEN 'OPEN' THEN 0 WHEN 'PROCESSING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END,
                     feedback.updated_at DESC, feedback.id DESC
            </script>
            """)
    List<FeedbackSummary> findFeedback(@Param("query") FeedbackQuery query);

    @Select(SUMMARY_SELECT + " WHERE feedback.id = #{id}")
    FeedbackSummary findSummary(long id);

    @Select(SUMMARY_SELECT + " WHERE feedback.visit_record_id = #{visitRecordId}")
    FeedbackSummary findByVisitRecordId(long visitRecordId);

    @Select("""
            SELECT action.id, action.action_type AS actionType, action.from_status AS fromStatus,
                   action.to_status AS toStatus, action.handler_id AS handlerId,
                   handler.name AS handlerName, action.content, action.created_at AS createdAt,
                   action.created_by AS createdBy, creator.full_name AS createdByName
            FROM dbo.vis_feedback_action action
            LEFT JOIN dbo.org_employee handler ON handler.id = action.handler_id
            JOIN dbo.iam_user creator ON creator.id = action.created_by
            WHERE action.feedback_id = #{feedbackId}
            ORDER BY action.created_at, action.id
            """)
    List<FeedbackActionItem> findActions(long feedbackId);

    @Insert("""
            INSERT INTO dbo.vis_feedback (
                feedback_no, visit_task_id, visit_record_id, member_id, bill_id, store_id,
                channel, score, content, complaint_type, status, due_hours, due_at,
                created_at, created_by, updated_at, updated_by
            ) VALUES (
                #{feedbackNo}, #{visitTaskId}, #{visitRecordId}, #{memberId}, #{billId}, #{storeId},
                'VISIT', #{score}, #{content}, 'SERVICE', 'OPEN', #{dueHours}, #{dueAt},
                #{createdAt}, #{createdBy}, #{createdAt}, #{createdBy}
            )
            """)
    int insertFeedback(FeedbackDraft draft);

    @Insert("""
            INSERT INTO dbo.vis_feedback_action (
                feedback_id, action_type, from_status, to_status, handler_id, content, created_at, created_by
            ) VALUES (
                #{feedbackId}, 'CREATED', NULL, 'OPEN', NULL, N'回访标记客诉后自动建单', #{createdAt}, #{operatorId}
            )
            """)
    int insertCreatedAction(
            @Param("feedbackId") long feedbackId,
            @Param("createdAt") java.time.LocalDateTime createdAt,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.vis_feedback
            SET status = #{update.status}, handler_id = #{update.handlerId},
                handle_result = CASE
                    WHEN #{update.actionType} = 'REOPENED' THEN NULL
                    WHEN #{update.handleResult} IS NULL THEN handle_result
                    ELSE #{update.handleResult} END,
                handled_at = CASE
                    WHEN #{update.actionType} = 'REOPENED' THEN NULL
                    WHEN #{update.actionType} IN ('RESOLVED', 'CLOSED') THEN COALESCE(handled_at, sysdatetime())
                    ELSE handled_at END,
                resolved_at = CASE
                    WHEN #{update.actionType} = 'REOPENED' THEN NULL
                    WHEN #{update.actionType} = 'RESOLVED' THEN sysdatetime()
                    ELSE resolved_at END,
                closed_at = CASE
                    WHEN #{update.actionType} = 'REOPENED' THEN NULL
                    WHEN #{update.actionType} = 'CLOSED' THEN sysdatetime()
                    ELSE closed_at END,
                due_hours = CASE WHEN #{update.actionType} = 'REOPENED'
                                 THEN #{update.dueHours} ELSE due_hours END,
                due_at = CASE WHEN #{update.actionType} = 'REOPENED'
                              THEN #{update.dueAt} ELSE due_at END,
                updated_at = sysdatetime(), updated_by = #{update.operatorId}
            WHERE id = #{update.id} AND status = #{update.expectedStatus}
            """)
    int updateFeedback(@Param("update") FeedbackUpdate update);

    @Insert("""
            INSERT INTO dbo.vis_feedback_action (
                feedback_id, action_type, from_status, to_status, handler_id, content, created_by
            ) VALUES (
                #{update.id}, #{update.actionType}, #{update.expectedStatus}, #{update.status},
                #{update.handlerId}, COALESCE(#{update.content}, #{update.handleResult}), #{update.operatorId}
            )
            """)
    int insertAction(@Param("update") FeedbackUpdate update);
}
