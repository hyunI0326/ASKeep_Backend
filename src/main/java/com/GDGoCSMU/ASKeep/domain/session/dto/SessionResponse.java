package com.GDGoCSMU.ASKeep.domain.session.dto;

import com.GDGoCSMU.ASKeep.domain.session.entity.Session;

import java.time.LocalDateTime;

/**
 * 날짜는 모두 "2026-11-20T14:00:00+09:00" 형식(KST 오프셋 포함)으로 나간다.
 * scheduledStartAt/scheduledEndAt: 예정 시작/종료 일시 (입력 안 했으면 null)
 * startedAt/endedAt: 발표자가 실제로 시작/종료한 시각
 */
public record SessionResponse(
        Long sessionId,
        String title,
        String description,
        Long presenterId,
        String presenterName,
        String entryCode,
        String status,
        LocalDateTime scheduledStartAt,
        LocalDateTime scheduledEndAt,
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
                s.getScheduledStartAt(), s.getScheduledEndAt(),
                s.getStartedAt(), s.getEndedAt(), s.getCreatedAt());
    }
}