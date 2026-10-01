package com.GDGoCSMU.ASKeep.domain.session.summary.dto;

import com.GDGoCSMU.ASKeep.domain.session.summary.SessionSummary;

import java.time.LocalDateTime;
import java.util.List;

public record SummaryResponse(
        Long sessionId,
        String status,
        String summary,
        List<String> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SummaryResponse from(SessionSummary s) {
        return new SummaryResponse(s.getSession().getId(), s.getStatus().name(), s.getSummary(),
                List.copyOf(s.getTags()), s.getCreateAt(), s.getUpdateAt());
    }
}
