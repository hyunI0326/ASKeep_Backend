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
    /** 입장 코드 포함 — 발표자·참여자 본인에게 주는 응답에만 쓸 것 */
    public static SessionResponse from(Session s) {
        return from(s, true);
    }

    /** showEntryCode가 false면 entryCode를 null로 숨긴다 (발표자·참여자가 아닌 사람에게) */
    public static SessionResponse from(Session s, boolean showEntryCode) {
        return new SessionResponse(
                s.getId(), s.getTitle(), s.getDescription(),
                s.getPresenter().getId(), s.getPresenter().getName(),
                showEntryCode ? s.getEntryCode() : null, s.getStatus().name(),
                s.getStartedAt(), s.getEndedAt(), s.getCreatedAt());
    }
}
