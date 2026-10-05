package com.GDGoCSMU.ASKeep.domain.question;

import com.GDGoCSMU.ASKeep.domain.answer.AnswerRepository;
import com.GDGoCSMU.ASKeep.domain.answer.AnswerResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionResponse(Long id, Long sessionId, String content, boolean anonymous,
                               UserSummary author, AiStatus aiStatus, List<AnswerResponse> answers,
                               LocalDateTime createdAt, LocalDateTime updatedAt,
                               @JsonInclude(JsonInclude.Include.NON_NULL) Boolean mine) {

    /**
     * REST 응답용. 보는 사람에 따라 달라진다.
     * - 익명 질문의 작성자: 발표자와 작성자 본인에게만 보임
     * - mine: 요청한 사람이 작성자인지
     */
    public static QuestionResponse forViewer(Question question, AnswerRepository answerRepository, Long viewerId) {
        boolean mine = question.getUser().getId().equals(viewerId);
        boolean presenter = question.getSession().isPresenter(viewerId);
        boolean showAuthor = !question.isAnonymous() || mine || presenter;
        return build(question, answerRepository, showAuthor, mine);
    }

    /**
     * 웹소켓 알림용. 모든 구독자에게 같은 내용이 가므로
     * 익명 질문의 작성자는 항상 가리고, mine은 넣지 않는다 (JSON에서 빠짐).
     */
    public static QuestionResponse forBroadcast(Question question, AnswerRepository answerRepository) {
        return build(question, answerRepository, !question.isAnonymous(), null);
    }

    private static QuestionResponse build(Question question, AnswerRepository answerRepository,
                                          boolean showAuthor, Boolean mine) {
        UserSummary author = showAuthor
                ? new UserSummary(question.getUser().getId(), question.getUser().getName())
                : null;
        List<AnswerResponse> answers = answerRepository.findByQuestion_IdOrderByIdAsc(question.getId())
                .stream().map(AnswerResponse::from).toList();
        return new QuestionResponse(question.getId(), question.getSession().getId(), question.getContent(),
                question.isAnonymous(), author, question.getAiStatus(), answers,
                question.getCreateAt(), question.getUpdateAt(), mine);
    }

    public record UserSummary(Long id, String username) {}
}