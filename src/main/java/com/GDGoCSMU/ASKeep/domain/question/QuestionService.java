package com.GDGoCSMU.ASKeep.domain.question;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;
import com.GDGoCSMU.ASKeep.global.websocket.RealtimeEventType;
import com.GDGoCSMU.ASKeep.global.websocket.SessionTopicEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.GDGoCSMU.ASKeep.domain.answer.Answer;
import com.GDGoCSMU.ASKeep.domain.answer.AnswerRepository;
import com.GDGoCSMU.ASKeep.domain.answer.AnswerType;
import com.GDGoCSMU.ASKeep.domain.material.client.AiClientServer;
import com.GDGoCSMU.ASKeep.domain.question.dto.AiAnswerRequest;
import com.GDGoCSMU.ASKeep.domain.question.dto.AiAnswerResponse;
import com.GDGoCSMU.ASKeep.domain.session.SessionAccessService;
import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import com.GDGoCSMU.ASKeep.domain.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final SessionAccessService sessionAccess;
    private final UserService userService;
    private final AiClientServer aiClientServer;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public Question create(Long sessionId, Long userId, String content, boolean anonymous) {
        Session session = sessionAccess.requireLiveMember(sessionId, userId);
        Question question = questionRepository.save(Question.builder().content(content).session(session)
                .user(userService.getUser(userId)).anonymous(anonymous).build());
        eventPublisher.publishEvent(new SessionTopicEvent(sessionId, RealtimeEventType.QUESTION_CREATED,
                QuestionResponse.forBroadcast(question, answerRepository)));
        processAsync(question.getId(), sessionId, content);
        return question;
    }

    public Page<Question> list(Long sessionId, Long userId, Pageable pageable) {
        sessionAccess.requireMember(sessionId, userId);
        return questionRepository.findBySession_IdOrderByIdDesc(sessionId, pageable);
    }

    public List<Question> after(Long sessionId, Long userId, Long afterId) {
        sessionAccess.requireMember(sessionId, userId);
        return questionRepository.findBySession_IdAndIdGreaterThanOrderByIdAsc(sessionId, afterId, org.springframework.data.domain.PageRequest.of(0, 100));
    }

    public Question get(Long questionId, Long userId) {
        Question question = find(questionId);
        sessionAccess.requireMember(question.getSession().getId(), userId);
        return question;
    }

    public Question update(Long id, Long userId, QuestionRequests.Update request) {
        Question question = get(id, userId);
        if (!question.getUser().getId().equals(userId)) throw new org.springframework.security.access.AccessDeniedException("질문 작성자만 수정할 수 있습니다.");
        if (question.getAiStatus() != AiStatus.PENDING) throw new IllegalStateException("AI 처리 전 질문만 수정할 수 있습니다.");
        if (request.content() != null) {
            if (request.content().isBlank()) throw new IllegalArgumentException("content는 비어 있을 수 없습니다.");
            question.setContent(request.content());
        }
        if (request.anonymous() != null) question.setAnonymous(request.anonymous());
        Question saved = questionRepository.save(question);
        publishUpdated(saved.getId(), saved.getSession().getId());
        return saved;
    }

    public void delete(Long id, Long userId) {
        Question question = get(id, userId);
        boolean host = question.getSession().isPresenter(userId);
        if (!host && !question.getUser().getId().equals(userId)) throw new org.springframework.security.access.AccessDeniedException("질문 작성자 또는 host만 삭제할 수 있습니다.");
        Long sessionId = question.getSession().getId();
        questionRepository.delete(question);
        eventPublisher.publishEvent(new SessionTopicEvent(sessionId, RealtimeEventType.QUESTION_DELETED, Map.of("questionId", id)));
    }

    public Question retry(Long id, Long userId) {
        Question question = get(id, userId);
        sessionAccess.requireHost(question.getSession().getId(), userId);
        question.retryAiProcessing();
        questionRepository.save(question);
        publishUpdated(question.getId(), question.getSession().getId());
        processAsync(question.getId(), question.getSession().getId(), question.getContent());
        return question;
    }

    public Answer answer(Long questionId, Long userId, String content) {
        Question question = get(questionId, userId);
        sessionAccess.requireHost(question.getSession().getId(), userId);
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content는 비어 있을 수 없습니다.");
        Answer saved = answerRepository.save(Answer.builder().content(content).type(AnswerType.PRESENTER)
                .question(question).author(userService.getUser(userId)).build());
        publishUpdated(questionId, question.getSession().getId());
        return saved;
    }

    public List<Answer> answers(Long questionId, Long userId) {
        get(questionId, userId);
        return answerRepository.findByQuestion_IdOrderByIdAsc(questionId);
    }

    public Answer updateAnswer(Long answerId, Long userId, String content) {
        Answer answer = answerRepository.findById(answerId).orElseThrow(() -> new EntityNotFoundException("답변을 찾을 수 없습니다."));
        Long questionId = answer.getQuestion().getId();
        Long sessionId = answer.getQuestion().getSession().getId();
        sessionAccess.requireMember(sessionId, userId);
        if (answer.getType() != AnswerType.PRESENTER || answer.getAuthor() == null || !answer.getAuthor().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("작성한 발표자 답변만 수정할 수 있습니다.");
        }
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content는 비어 있을 수 없습니다.");
        answer.setContent(content);
        Answer saved = answerRepository.save(answer);
        publishUpdated(questionId, sessionId);
        return saved;
    }

    public void deleteAnswer(Long answerId, Long userId) {
        Answer answer = answerRepository.findById(answerId).orElseThrow(() -> new EntityNotFoundException("답변을 찾을 수 없습니다."));
        Long questionId = answer.getQuestion().getId();
        Long sessionId = answer.getQuestion().getSession().getId();
        sessionAccess.requireMember(sessionId, userId);
        boolean host = answer.getQuestion().getSession().isPresenter(userId);
        boolean author = answer.getAuthor() != null && answer.getAuthor().getId().equals(userId);
        if (!host && !author) throw new org.springframework.security.access.AccessDeniedException("작성자 또는 host만 삭제할 수 있습니다.");
        if (answer.getType() == AnswerType.AI && !host) throw new org.springframework.security.access.AccessDeniedException("AI 답변은 host만 삭제할 수 있습니다.");
        answerRepository.delete(answer);
        publishUpdated(questionId, sessionId);
    }

    private Question find(Long id) {
        return questionRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("질문을 찾을 수 없습니다."));
    }

    private void processAsync(Long questionId, Long sessionId, String content) {
        // ponytail: common pool is sufficient for the initial single-instance service; use a bounded executor if AI volume grows.
        CompletableFuture.runAsync(() -> {
            Question question = questionRepository.findById(questionId).orElse(null);
            if (question == null) return;
            question.startAiProcessing();
            questionRepository.save(question);
            publishUpdated(questionId, sessionId);
            try {
                AiAnswerResponse result = aiClientServer.answer(new AiAnswerRequest(sessionId, content));
                if (result == null || result.answer() == null || result.answer().isBlank()) throw new IllegalStateException("AI 답변이 비어 있습니다.");
                answerRepository.save(Answer.builder().content(result.answer()).type(AnswerType.AI).question(question).author(null).build());
                question.completeAiProcessing();
            } catch (Exception exception) {
                log.warn("AI 답변 생성 실패 questionId={}", questionId, exception);
                question.failAiProcessing();
            }
            questionRepository.save(question);
            publishUpdated(questionId, sessionId);
        });
    }

    /** 질문의 최신 상태(답변 포함)를 QUESTION_UPDATED로 보낸다. 비동기 스레드에서도 안전하게 동작한다. */
    private void publishUpdated(Long questionId, Long sessionId) {
        try {
            QuestionResponse response = transactionTemplate.execute(status ->
                    questionRepository.findById(questionId)
                            .map(question -> QuestionResponse.forBroadcast(question, answerRepository))
                            .orElse(null));
            if (response != null) {
                eventPublisher.publishEvent(new SessionTopicEvent(sessionId, RealtimeEventType.QUESTION_UPDATED, response));
            }
        } catch (Exception exception) {
            log.warn("질문 알림 발송 실패 questionId={}", questionId, exception);
        }
    }
}
