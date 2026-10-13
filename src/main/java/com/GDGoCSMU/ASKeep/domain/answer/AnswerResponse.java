package com.GDGoCSMU.ASKeep.domain.answer;

import java.time.LocalDateTime;
import java.util.List;

public record AnswerResponse(Long id, Long questionId, String content, AnswerType type,
                             UserSummary author, List<SourceResponse> sources, LocalDateTime createdAt) {
    public static AnswerResponse from(Answer answer) {
        UserSummary author = answer.getAuthor() == null ? null :
                new UserSummary(answer.getAuthor().getId(), answer.getAuthor().getName());
        List<SourceResponse> sources = answer.getSources().stream().map(SourceResponse::from).toList();
        return new AnswerResponse(answer.getId(), answer.getQuestion().getId(), answer.getContent(), answer.getType(),
                author, sources, answer.getCreateAt());
    }
    public record UserSummary(Long id, String username) {}

    /** AI 답변 출처: 어느 자료의 몇 페이지인지 */
    public record SourceResponse(Long materialId, String fileName, Integer pageNumber, Double similarity) {
        static SourceResponse from(AnswerSource source) {
            return new SourceResponse(source.getMaterialId(), source.getFileName(), source.getPageNumber(), source.getSimilarity());
        }
    }
}