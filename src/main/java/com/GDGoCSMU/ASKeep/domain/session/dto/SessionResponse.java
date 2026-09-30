package com.GDGoCSMU.ASKeep.domain.session.dto;

import com.GDGoCSMU.ASKeep.domain.session.entity.Session;

import java.time.LocalDateTime;

public record SessionResponse(
        Long sessionId,
        String title,
        Long presenterId,
        String presenterName,
        String entryCode,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt
) {
    public static SessionResponse from(Session s) {
        return new SessionResponse(
                s.getId(), s.getTitle(),
                s.getPresenter().getId(), s.getPresenter().getName(),
                s.getEntryCode(), s.getStatus().name(),
                s.getStartedAt(), s.getEndedAt(), s.getCreatedAt());
    }
}
