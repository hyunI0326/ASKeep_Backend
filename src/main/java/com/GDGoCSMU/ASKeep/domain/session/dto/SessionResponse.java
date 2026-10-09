package com.GDGoCSMU.ASKeep.domain.session.dto;

import com.GDGoCSMU.ASKeep.domain.session.entity.Session;

import java.time.LocalDateTime;

public record SessionResponse(
        Long sessionId,
        String title,
        String description,
        Long presenterId,
        String presenterName,
        String entryCode,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt
) {
    /** 입장 코드 포함 — 발표자 또는 이미 참여한 청자에게 주는 응답 */
    public static SessionResponse from(Session s) {
        return from(s, true);
    }

    /** showEntryCode가 false면 미참여자에게 entryCode를 숨긴다 */
    public static SessionResponse from(Session s, boolean showEntryCode) {
        return new SessionResponse(
                s.getId(), s.getTitle(), s.getDescription(),
                s.getPresenter().getId(), s.getPresenter().getName(),
                showEntryCode ? s.getEntryCode() : null, s.getStatus().name(),
                s.getStartedAt(), s.getEndedAt(), s.getCreatedAt());
    }
}
