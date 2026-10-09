package com.GDGoCSMU.ASKeep.domain.question;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;
import com.GDGoCSMU.ASKeep.global.websocket.RealtimeEventType;
import com.GDGoCSMU.ASKeep.global.websocket.SessionTopicEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.GDGoCSMU.ASKeep.domain.answer.Answer;
import com.GDGoCSMU.ASKeep.domain.answer.AnswerRepository;
import com.GDGoCSMU.ASKeep.domain.answer.AnswerType;
import com.GDGoCSMU.ASKeep.domain.material.Material;
import com.GDGoCSMU.ASKeep.domain.material.MaterialRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;

import java.util.LinkedHashMap;
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
    private final MaterialRepository materialRepository;
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

    public Page<Question> list(Long sessionId, Long userId, Pageable pageable, String sort) {
        sessionAccess.requireMember(sessionId, userId);
        if ("popular".equals(sort)) return questionRepository.findPopularBySessionId(sessionId, pageable);
        return questionRepository.findBySession_IdOrderByIdDesc(sessionId, pageable);
    }

    @Transactional
    public Question markAnswered(Long id, Long userId, boolean answered) {
        Question question = findForUpdate(id);
        sessionAccess.requireHost(question.getSession().getId(), userId);
        if (question.isAnswered() != answered) {
            question.markAnswered(answered);
            questionRepository.saveAndFlush(question);
            publishUpdated(id, question.getSession().getId());
        }
        return question;
    }

    @Transactional
    public Question like(Long id, Long userId, boolean liked) {
        Question question = findForUpdate(id);
        sessionAccess.requireMember(question.getSession().getId(), userId);
        boolean changed = liked ? question.getLikedBy().add(userService.getUser(userId))
                : question.getLikedBy().removeIf(user -> user.getId().equals(userId));
        if (changed) {
            question.touch();
            questionRepository.saveAndFlush(question);
            publishUpdated(id, question.getSession().getId());
        }
        return question;
    }

    @Transactional
    public Question requestPresenter(Long id, Long userId) {
        Question question = findForUpdate(id);
        sessionAccess.requireLiveMember(question.getSession().getId(), userId);
        if (!question.getUser().getId().equals(userId)) throw new AccessDeniedException("질문 작성자만 요청할 수 있습니다.");
        if (!question.isPresenterRequested()) {
            question.requestPresenter();
            questionRepository.saveAndFlush(question);
            publishUpdated(id, question.getSession().getId());
        }
        return question;
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

    @Transactional
    public Question update(Long id, Long userId, QuestionRequests.Update request) {
        Question question = findForUpdate(id);
        sessionAccess.requireMember(question.getSession().getId(), userId);
        if (!question.getUser().getId().equals(userId)) throw new org.springframework.security.access.AccessDeniedException("질문 작성자만 수정할 수 있습니다.");
        if (question.getAiStatus() != AiStatus.PENDING) throw new IllegalStateException("AI 처리 전 질문만 수정할 수 있습니다.");
        if (request.content() != null) {
            if (request.content().isBlank()) throw new IllegalArgumentException("content는 비어 있을 수 없습니다.");
            question.setContent(request.content());
        }
        if (request.anonymous() != null) question.setAnonymous(request.anonymous());
        Question saved = questionRepository.saveAndFlush(question);
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
        Question question = transactionTemplate.execute(status -> {
            Question locked = findForUpdate(id);
            sessionAccess.requireHost(locked.getSession().getId(), userId);
            locked.retryAiProcessing();
            questionRepository.saveAndFlush(locked);
            publishUpdated(locked.getId(), locked.getSession().getId());
            return locked;
        });
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

    private Question findForUpdate(Long id) {
        return questionRepository.findForUpdate(id).orElseThrow(() -> new EntityNotFoundException("질문을 찾을 수 없습니다."));
    }

    private void processAsync(Long questionId, Long sessionId, String content) {
        // ponytail: common pool is sufficient for the initial single-instance service; use a bounded executor if AI volume grows.
        CompletableFuture.runAsync(() -> {
            Question question = questionRepository.findById(questionId).orElse(null);
            if (question == null) return;
            if (questionRepository.updateAiStatus(questionId, AiStatus.PROCESSING, LocalDateTime.now()) == 0) return;
            publishUpdated(questionId, sessionId);
            try {
                AiAnswerResponse result = aiClientServer.answer(new AiAnswerRequest(sessionId, content));
                if (result == null || result.answer() == null || result.answer().isBlank()) throw new IllegalStateException("AI 답변이 비어 있습니다.");
                                Answer aiAnswer = Answer.builder().content(result.answer()).type(AnswerType.AI).question(question).author(null).build();
                addSources(aiAnswer, result.sources());
                answerRepository.save(aiAnswer);
                questionRepository.updateAiStatus(questionId, AiStatus.COMPLETED, LocalDateTime.now());
            } catch (Exception exception) {
                log.warn("AI 답변 생성 실패 questionId={}", questionId, exception);
                questionRepository.updateAiStatus(questionId, AiStatus.FAILED, LocalDateTime.now());
            }
            publishUpdated(questionId, sessionId);
        });
    }

        /**
     * AI가 참고한 자료 조각을 답변 출처로 붙인다.
     * 같은 자료의 같은 페이지에서 조각이 여러 개 나오면 하나로 합치고, 관련도는 가장 높은 값을 쓴다.
     */
    private void addSources(Answer answer, List<AiAnswerResponse.Source> sources) {
        if (sources == null) return;
        Map<String, AiAnswerResponse.Source> best = new LinkedHashMap<>();
        for (AiAnswerResponse.Source source : sources) {
            if (source == null || source.materialId() == null) continue;
            String key = source.materialId() + "-" + source.pageNumber();
            AiAnswerResponse.Source kept = best.get(key);
            if (kept == null || similarity(source) > similarity(kept)) best.put(key, source);
        }
        Map<Long, String> fileNames = new LinkedHashMap<>();
        for (AiAnswerResponse.Source source : best.values()) {
            String fileName = fileNames.computeIfAbsent(source.materialId(),
                    id -> materialRepository.findById(id).map(Material::getFileName).orElse(null));
            answer.addSource(source.materialId(), fileName, source.pageNumber(), source.similarity());
        }
    }

    private static double similarity(AiAnswerResponse.Source source) {
        return source.similarity() == null ? 0 : source.similarity();
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
