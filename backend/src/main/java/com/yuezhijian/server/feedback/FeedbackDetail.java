package com.yuezhijian.server.feedback;

import java.util.List;

public record FeedbackDetail(
        FeedbackSummary feedback,
        List<FeedbackActionItem> actions) {
    public FeedbackDetail {
        actions = List.copyOf(actions);
    }
}
