package com.yuezhijian.server.feedback;

import com.yuezhijian.server.file.BusinessAttachmentItem;
import java.util.List;

public record FeedbackDetail(
        FeedbackSummary feedback,
        List<FeedbackActionItem> actions,
        List<BusinessAttachmentItem> attachments) {
    public FeedbackDetail {
        actions = List.copyOf(actions);
        attachments = List.copyOf(attachments);
    }
}
