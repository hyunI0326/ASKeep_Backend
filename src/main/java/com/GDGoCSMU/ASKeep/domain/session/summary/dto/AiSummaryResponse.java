package com.GDGoCSMU.ASKeep.domain.session.summary.dto;

import java.util.List;

/** AI 서버 POST /sessions/summary 응답 */
public record AiSummaryResponse(Long sessionId, String summary, List<String> tags) {}
