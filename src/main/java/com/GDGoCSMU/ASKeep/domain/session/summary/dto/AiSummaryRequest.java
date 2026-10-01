package com.GDGoCSMU.ASKeep.domain.session.summary.dto;

import java.util.List;

/** AI 서버 POST /sessions/summary 요청 */
public record AiSummaryRequest(Long sessionId, String title, String description, List<QuestionItem> questions) {

    /** answers: "[AI] ..." 또는 "[발표자] ..." 형식 */
    public record QuestionItem(String question, List<String> answers) {}
}
