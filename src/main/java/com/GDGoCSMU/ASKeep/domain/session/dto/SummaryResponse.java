package com.GDGoCSMU.ASKeep.domain.session.dto;

import com.GDGoCSMU.ASKeep.domain.session.entity.SessionSummary;
import com.GDGoCSMU.ASKeep.domain.session.entity.SummarySection;

import java.time.LocalDateTime;
import java.util.List;

public record SummaryResponse(
        Long sessionId,
        String status,
        String summary,
        List<String> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<SummarySection> sections
) {
    public static SummaryResponse from(SessionSummary s) {
        return new SummaryResponse(s.getSession().getId(), s.getStatus().name(), s.getSummary(),
                List.copyOf(s.getTags()), s.getCreateAt(), s.getUpdateAt(),
                s.getSections().isEmpty() && s.getSummary() != null && !s.getSummary().isBlank()
                        ? List.of(new SummarySection("세션 요약", s.getSummary())) : List.copyOf(s.getSections()));
    }
}
