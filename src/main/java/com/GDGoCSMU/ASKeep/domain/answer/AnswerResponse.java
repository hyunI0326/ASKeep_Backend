package com.GDGoCSMU.ASKeep.domain.answer;

import java.time.LocalDateTime;

public record AnswerResponse(Long id, Long questionId, String content, AnswerType type,
                             UserSummary author, LocalDateTime createdAt) {
    public static AnswerResponse from(Answer answer) {
        UserSummary author = answer.getAuthor() == null ? null :
                new UserSummary(answer.getAuthor().getId(), answer.getAuthor().getName());
        return new AnswerResponse(answer.getId(), answer.getQuestion().getId(), answer.getContent(), answer.getType(),
                author, answer.getCreateAt());
    }
    public record UserSummary(Long id, String username) {}
}
