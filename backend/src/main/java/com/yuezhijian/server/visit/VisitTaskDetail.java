package com.yuezhijian.server.visit;

import java.util.List;

public record VisitTaskDetail(
        VisitTaskSummary task,
        List<VisitParticipantItem> participants,
        List<VisitRecordItem> records) {
    public VisitTaskDetail {
        participants = List.copyOf(participants);
        records = List.copyOf(records);
    }
}
