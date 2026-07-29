package com.yuezhijian.server.feedback;

import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.file.BusinessAttachmentItem;
import com.yuezhijian.server.file.FileObjectService;
import com.yuezhijian.server.file.StoredFileDownload;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import com.yuezhijian.server.settings.SystemSettingsService;
import com.yuezhijian.server.visit.VisitRecordItem;
import com.yuezhijian.server.visit.VisitTaskDetail;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ServiceFeedbackService {
    private static final Set<String> STATUSES = Set.of("OPEN", "PROCESSING", "RESOLVED", "CLOSED");
    private static final Set<String> ACTIONS = Set.of("ASSIGN", "NOTE", "RESOLVE", "CLOSE", "REOPEN");

    private final FeedbackRepository repository;
    private final MasterDataRepository masterData;
    private final AccessCatalogService accessCatalog;
    private final FeedbackNumberGenerator numbers;
    private final SystemSettingsService settings;
    private final FileObjectService files;

    public ServiceFeedbackService(
            FeedbackRepository repository,
            MasterDataRepository masterData,
            AccessCatalogService accessCatalog,
            FeedbackNumberGenerator numbers,
            SystemSettingsService settings,
            FileObjectService files) {
        this.repository = repository;
        this.masterData = masterData;
        this.accessCatalog = accessCatalog;
        this.numbers = numbers;
        this.settings = settings;
        this.files = files;
    }

    public List<FeedbackSummary> feedback(
            Long storeId, Long handlerId, Integer score, String status, Boolean overdue, String keyword) {
        if (storeId != null && accessCatalog.stores().stream().noneMatch(store -> store.id() == storeId)) {
            throw new IllegalArgumentException("无权查看所选门店的服务反馈");
        }
        if (score != null && (score < 1 || score > 5)) throw new IllegalArgumentException("满意度必须为1至5分");
        String normalizedStatus = trimToNull(status) == null ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("服务反馈状态无效");
        }
        return repository.feedback(new FeedbackQuery(
                storeId, handlerId, score, normalizedStatus, overdue, trimToNull(keyword)));
    }

    public FeedbackDetail detail(long id) {
        return enrich(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("服务反馈不存在")));
    }

    public BusinessAttachmentItem uploadAttachment(
            long id, MultipartFile upload, String username) {
        FeedbackSummary feedback = detail(id).feedback();
        long operatorId = accessCatalog.userIdentity(username).id();
        return files.upload(
                "SERVICE_FEEDBACK", id, feedback.storeId(), "SERVICE_FEEDBACK_ATTACHMENT", "EVIDENCE",
                upload, operatorId);
    }

    public StoredFileDownload downloadAttachment(long id, long attachmentId) {
        detail(id);
        return files.download("SERVICE_FEEDBACK", id, attachmentId);
    }

    public void removeAttachment(long id, long attachmentId, String username) {
        detail(id);
        files.remove(
                "SERVICE_FEEDBACK", id, attachmentId, accessCatalog.userIdentity(username).id());
    }

    @Transactional
    public FeedbackDetail ensureFromVisit(
            VisitTaskDetail task, VisitRecordItem record, long operatorId) {
        if (!record.complaintFlag()) return null;
        var existing = repository.findByVisitRecordId(record.id());
        if (existing.isPresent()) return existing.get();
        LocalDateTime createdAt = record.createdAt() == null ? LocalDateTime.now() : record.createdAt();
        int dueHours = feedbackDueHours();
        return repository.create(new FeedbackDraft(
                numbers.feedbackNo(), task.task().id(), record.id(), task.task().memberId(),
                task.task().customerName(), task.task().maskedMobile(), task.task().billId(), task.task().billNo(),
                task.task().storeId(), task.task().storeName(), record.satisfactionScore(),
                record.content(), createdAt, dueHours, createdAt.plusHours(dueHours), operatorId));
    }

    @Transactional
    public FeedbackDetail handle(long id, HandleFeedbackRequest request, String username) {
        FeedbackDetail detail = detail(id);
        FeedbackSummary feedback = detail.feedback();
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) throw new IllegalArgumentException("服务反馈处理动作无效");
        String content = trimToNull(request.content());
        String result = trimToNull(request.result());
        Long handlerId = request.handlerId() == null ? feedback.handlerId() : request.handlerId();
        String nextStatus;
        String actionType;
        Integer dueHours = null;
        LocalDateTime dueAt = null;
        switch (action) {
            case "ASSIGN" -> {
                requireStatus(feedback.status(), Set.of("OPEN", "PROCESSING"), "只有待处理或处理中的反馈可以分配");
                if (request.handlerId() == null) throw new IllegalArgumentException("分配时必须选择负责人");
                nextStatus = "PROCESSING";
                actionType = "ASSIGNED";
            }
            case "NOTE" -> {
                requireStatus(feedback.status(), Set.of("PROCESSING", "RESOLVED"), "当前状态不能添加处理备注");
                if (content == null) throw new IllegalArgumentException("处理备注不能为空");
                nextStatus = feedback.status();
                actionType = "NOTE";
            }
            case "RESOLVE" -> {
                requireStatus(feedback.status(), Set.of("PROCESSING"), "只有处理中的反馈可以标记解决");
                if (handlerId == null) throw new IllegalArgumentException("解决反馈前必须分配负责人");
                if (result == null) throw new IllegalArgumentException("标记解决时必须填写处理结果");
                nextStatus = "RESOLVED";
                actionType = "RESOLVED";
            }
            case "CLOSE" -> {
                requireStatus(feedback.status(), Set.of("RESOLVED"), "只有已解决的反馈可以关闭");
                nextStatus = "CLOSED";
                actionType = "CLOSED";
                if (result == null) result = feedback.handleResult();
            }
            case "REOPEN" -> {
                requireStatus(feedback.status(), Set.of("RESOLVED", "CLOSED"), "只有已解决或已关闭的反馈可以重新打开");
                if (handlerId == null) throw new IllegalArgumentException("重新打开前必须指定负责人");
                if (content == null) throw new IllegalArgumentException("重新打开时必须填写原因");
                nextStatus = "PROCESSING";
                actionType = "REOPENED";
                result = null;
                dueHours = feedbackDueHours();
                dueAt = LocalDateTime.now().plusHours(dueHours);
            }
            default -> throw new IllegalArgumentException("服务反馈处理动作无效");
        }
        if (handlerId != null) validateHandler(handlerId, feedback.storeId());
        long operatorId = accessCatalog.userIdentity(username).id();
        return enrich(repository.update(new FeedbackUpdate(
                id, feedback.status(), nextStatus, handlerId, result, actionType, content,
                dueHours, dueAt, operatorId)));
    }

    private FeedbackDetail enrich(FeedbackDetail detail) {
        return new FeedbackDetail(
                detail.feedback(), detail.actions(), files.attachments("SERVICE_FEEDBACK", detail.feedback().id()));
    }

    private int feedbackDueHours() {
        return settings.integerValue("VISIT", "SERVICE_FEEDBACK_DUE_HOURS", 24, 1, 720);
    }

    private void validateHandler(long handlerId, long storeId) {
        masterData.employees(storeId, null).stream()
                .filter(item -> item.id() == handlerId && "ACTIVE".equals(item.status()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("负责人不属于当前门店或已停用"));
    }

    private static void requireStatus(String actual, Set<String> expected, String message) {
        if (!expected.contains(actual)) throw new IllegalArgumentException(message);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
