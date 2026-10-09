package com.GDGoCSMU.ASKeep.domain.session.dto;

/**
 * 내 세션 기록 한 줄.
 * myRole: PRESENTER(내가 만든 세션) / AUDIENCE(참여한 세션)
 * summaryStatus: 세션 요약 상태 (PENDING·PROCESSING·COMPLETED·FAILED, 종료 전이면 null)
 */
public record MySessionResponse(String myRole, SessionResponse session, String summaryStatus,
                                java.util.List<String> tags, long questionCount,
                                java.time.LocalDateTime joinedAt) {}
