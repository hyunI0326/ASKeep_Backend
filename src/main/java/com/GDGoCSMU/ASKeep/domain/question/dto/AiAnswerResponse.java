package com.GDGoCSMU.ASKeep.domain.question.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** AI 서버 POST /ai/answer 응답. 출처는 AI가 참고한 자료 조각 목록 (최대 5개). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnswerResponse(Long sessionId, String question, String answer, List<Source> sources) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(Long materialId, Integer pageNumber, Double similarity) {}
}