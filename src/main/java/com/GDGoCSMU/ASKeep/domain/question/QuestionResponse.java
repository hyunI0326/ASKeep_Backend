package com.GDGoCSMU.ASKeep.domain.question;

import com.GDGoCSMU.ASKeep.domain.answer.AnswerRepository;
import com.GDGoCSMU.ASKeep.domain.answer.AnswerResponse;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionResponse(Long id, Long sessionId, String content, boolean anonymous,
                               UserSummary author, AiStatus aiStatus, List<AnswerResponse> answers,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static QuestionResponse from(Question question, AnswerRepository answerRepository) {
        UserSummary author = question.isAnonymous() ? null :
                new UserSummary(question.getUser().getId(), question.getUser().getName());
        List<AnswerResponse> answers = answerRepository.findByQuestion_IdOrderByIdAsc(question.getId())
                .stream().map(AnswerResponse::from).toList();
        return new QuestionResponse(question.getId(), question.getSession().getId(), question.getContent(),
                question.isAnonymous(), author, question.getAiStatus(), answers, question.getCreateAt(), question.getUpdateAt());
    }
    public record UserSummary(Long id, String username) {}
}
