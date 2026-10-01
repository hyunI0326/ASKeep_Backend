package com.GDGoCSMU.ASKeep.domain.session.service;

/** 세션 종료 또는 요약 재시도 시 발행. 커밋 후 SessionSummaryProcessor가 AI 요약을 시작한다. */
public record SummaryRequestedEvent(Long sessionId) {}
