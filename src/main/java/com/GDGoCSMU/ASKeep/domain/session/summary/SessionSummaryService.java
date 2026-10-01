package com.GDGoCSMU.ASKeep.domain.session.summary;

import com.GDGoCSMU.ASKeep.domain.answer.Answer;
import com.GDGoCSMU.ASKeep.domain.answer.AnswerType;
import com.GDGoCSMU.ASKeep.domain.question.Question;
import com.GDGoCSMU.ASKeep.domain.question.QuestionRepository;
import com.GDGoCSMU.ASKeep.domain.session.SessionParticipantRepository;
import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import com.GDGoCSMU.ASKeep.domain.session.repository.SessionRepository;
import com.GDGoCSMU.ASKeep.domain.session.summary.dto.AiSummaryRequest;
import com.GDGoCSMU.ASKeep.domain.session.summary.dto.SummaryResponse;
import com.GDGoCSMU.ASKeep.global.exception.BusinessException;
import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SessionSummaryService {

    /** AI에 보내는 질문 수 상한 (너무 긴 프롬프트 방지) */
    private static final int MAX_QUESTIONS = 300;
    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 30;

    private final SessionSummaryRepository summaryRepository;
    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final QuestionRepository questionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SessionSummaryService(SessionSummaryRepository summaryRepository, SessionRepository sessionRepository,
                                 SessionParticipantRepository participantRepository,
                                 QuestionRepository questionRepository, ApplicationEventPublisher eventPublisher) {
        this.summaryRepository = summaryRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.questionRepository = questionRepository;
        this.eventPublisher = eventPublisher;
    }

    /** 세션 종료 트랜잭션 안에서 호출: PENDING 요약을 만들고, 커밋 후 AI 요약이 시작되게 한다. */
    @Transactional
    public void requestFor(Session session) {
        if (!summaryRepository.existsBySession_Id(session.getId())) {
            summaryRepository.save(new SessionSummary(session));
        }
        eventPublisher.publishEvent(new SummaryRequestedEvent(session.getId()));
    }

    /** 세션 발표자·참여자만 조회 */
    public SummaryResponse get(Long userId, Long sessionId) {
        Session session = findSession(sessionId);
        if (!session.isPresenter(userId) && !participantRepository.existsBySession_IdAndUser_Id(sessionId, userId)) {
            throw new BusinessException(ErrorCode.NOT_SESSION_PARTICIPANT);
        }
        return SummaryResponse.from(findSummary(sessionId));
    }

    /** 발표자만, FAILED일 때만 다시 요청 */
    @Transactional
    public SummaryResponse retry(Long userId, Long sessionId) {
        Session session = findSession(sessionId);
        if (!session.isPresenter(userId)) throw new BusinessException(ErrorCode.NOT_SESSION_PRESENTER);
        SessionSummary summary = findSummary(sessionId);
        summary.retry();
        eventPublisher.publishEvent(new SummaryRequestedEvent(sessionId));
        return SummaryResponse.from(summary);
    }

    // --- 아래는 SessionSummaryProcessor(비동기)에서 호출 ---

    /** PENDING → PROCESSING으로 바꾸고 AI 요청 내용을 만든다. 처리할 게 없으면 null. */
    @Transactional
    public AiSummaryRequest startProcessing(Long sessionId) {
        SessionSummary summary = summaryRepository.findBySession_Id(sessionId).orElse(null);
        if (summary == null || !summary.startProcessing()) return null;

        Session session = summary.getSession();
        List<Question> questions = new ArrayList<>(questionRepository.findBySession_IdOrderByIdDesc(sessionId));
        Collections.reverse(questions);  // 오래된 순
        List<AiSummaryRequest.QuestionItem> items = questions.stream().limit(MAX_QUESTIONS)
                .map(q -> new AiSummaryRequest.QuestionItem(q.getContent(),
                        q.getAnswers().stream().map(SessionSummaryService::label).toList()))
                .toList();
        return new AiSummaryRequest(sessionId, session.getTitle(), session.getDescription(), items);
    }

    @Transactional
    public void complete(Long sessionId, String summaryText, List<String> tags) {
        summaryRepository.findBySession_Id(sessionId)  // 그 사이 세션이 삭제됐으면 무시
                .ifPresent(s -> s.complete(summaryText.trim(), cleanTags(tags)));
    }

    @Transactional
    public void fail(Long sessionId) {
        summaryRepository.findBySession_Id(sessionId).ifPresent(SessionSummary::fail);
    }

    private static String label(Answer answer) {
        return (answer.getType() == AnswerType.AI ? "[AI] " : "[발표자] ") + answer.getContent();
    }

    /** 앞뒤 공백·'#' 제거, 빈 값·중복 제거, 최대 10개·30자 */
    static List<String> cleanTags(List<String> tags) {
        if (tags == null) return List.of();
        Set<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) continue;
            String t = tag.replaceAll("[\\r\\n]", " ").trim().replaceFirst("^#+", "").trim();
            if (t.isEmpty()) continue;
            result.add(t.length() > MAX_TAG_LENGTH ? t.substring(0, MAX_TAG_LENGTH) : t);
            if (result.size() == MAX_TAGS) break;
        }
        return List.copyOf(result);
    }

    private Session findSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    private SessionSummary findSummary(Long sessionId) {
        return summaryRepository.findBySession_Id(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUMMARY_NOT_FOUND));
    }
}
