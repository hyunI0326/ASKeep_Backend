package com.GDGoCSMU.ASKeep.domain.session.service;

import com.GDGoCSMU.ASKeep.domain.session.dto.MySessionResponse;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionCreateRequest;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionResponse;
import com.GDGoCSMU.ASKeep.domain.session.repository.SessionSummaryRepository;
import com.GDGoCSMU.ASKeep.domain.user.domain.UserRole;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionUpdateRequest;
import com.GDGoCSMU.ASKeep.domain.session.SessionParticipant;
import com.GDGoCSMU.ASKeep.domain.session.SessionParticipantRepository;
import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import com.GDGoCSMU.ASKeep.domain.session.entity.SessionStatus;
import com.GDGoCSMU.ASKeep.domain.session.repository.SessionRepository;
import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import com.GDGoCSMU.ASKeep.domain.user.service.UserService;
import com.GDGoCSMU.ASKeep.domain.material.MaterialService;
import com.GDGoCSMU.ASKeep.global.exception.BusinessException;
import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import com.GDGoCSMU.ASKeep.global.websocket.RealtimeEventType;
import com.GDGoCSMU.ASKeep.global.websocket.SessionTopicEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class SessionService {

    private static final int MAX_CODE_ATTEMPTS = 10;

    private final SessionRepository sessionRepository;
    private final UserService userService;
    private final EntryCodeGenerator entryCodeGenerator;
    private final SessionParticipantRepository participantRepository;
    private final MaterialService materialService;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionSummaryService summaryService;
    private final SessionSummaryRepository summaryRepository;

    public SessionService(SessionRepository sessionRepository, UserService userService,
                          EntryCodeGenerator entryCodeGenerator,
                          SessionParticipantRepository participantRepository, MaterialService materialService,
                          ApplicationEventPublisher eventPublisher,
                          SessionSummaryService summaryService, SessionSummaryRepository summaryRepository) {
        this.sessionRepository = sessionRepository;
        this.userService = userService;
        this.entryCodeGenerator = entryCodeGenerator;
        this.participantRepository = participantRepository;
        this.materialService = materialService;
        this.eventPublisher = eventPublisher;
        this.summaryService = summaryService;
        this.summaryRepository = summaryRepository;
    }

    @Transactional
    public SessionResponse create(Long userId, SessionCreateRequest request) {
        User presenter = userService.getUser(userId);
        Session session = new Session(request.title().trim(), request.description(), presenter, newEntryCode());
        return SessionResponse.from(sessionRepository.save(session));
    }

    /** 입장 코드는 그 세션을 연 발표자에게만 보인다 (참여자 포함 나머지는 null) — 팀 결정 */
    public List<SessionResponse> getList(Long userId, SessionStatus status) {
        List<Session> sessions = (status == null)
                ? sessionRepository.findAllByOrderByCreatedAtDesc()
                : sessionRepository.findAllByStatusOrderByCreatedAtDesc(status);
        return sessions.stream()
                .map(s -> SessionResponse.from(s, s.isPresenter(userId)))
                .toList();
    }

    public SessionResponse getDetail(Long userId, Long sessionId) {
        Session session = findSession(sessionId);
        return SessionResponse.from(session, session.isPresenter(userId));
    }

    @Transactional
    public SessionResponse update(Long userId, Long sessionId, SessionUpdateRequest request) {
        Session session = findOwnedSession(userId, sessionId);
        if (request.title() != null) {
            session.changeTitle(request.title().trim());
        }
        if (request.description() != null) session.changeDescription(request.description());
        return SessionResponse.from(session);
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        Session session = findOwnedSession(userId, sessionId);
        materialService.deleteSessionFiles(sessionId);
        summaryRepository.deleteBySession_Id(sessionId);  // 요약이 세션을 참조하므로 먼저 삭제
        sessionRepository.delete(session);
    }

    /**
     * 내 세션 기록: 내가 만든 세션(PRESENTER) + 참여한 세션(AUDIENCE), 최신순.
     * role을 주면 그 역할만. 각 세션의 요약 상태(summaryStatus, 없으면 null)를 함께 준다.
     */
    public List<MySessionResponse> getMySessions(Long userId, UserRole role) {
        List<MySessionResponse> result = new ArrayList<>();
        if (role == null || role == UserRole.PRESENTER) {
            sessionRepository.findAllByPresenter_IdOrderByCreatedAtDesc(userId)
                    .forEach(s -> result.add(new MySessionResponse(UserRole.PRESENTER.name(), SessionResponse.from(s), null)));
        }
        if (role == null || role == UserRole.AUDIENCE) {
            sessionRepository.findJoinedByUserId(userId)
                    .forEach(s -> result.add(new MySessionResponse(UserRole.AUDIENCE.name(), SessionResponse.from(s, false), null)));
        }
        if (result.isEmpty()) return result;

        Map<Long, String> summaryStatus = new HashMap<>();
        summaryRepository.findBySession_IdIn(result.stream().map(r -> r.session().sessionId()).toList())
                .forEach(s -> summaryStatus.put(s.getSession().getId(), s.getStatus().name()));
        return result.stream()
                .map(r -> new MySessionResponse(r.myRole(), r.session(), summaryStatus.get(r.session().sessionId())))
                .sorted(Comparator.comparing((MySessionResponse r) -> r.session().createdAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** 청중이 발표자에게 받은 6자리 입장 코드로 참여한다. 이미 참여했으면 기존 참여 정보를 돌려준다. */
    @Transactional
    public SessionParticipant joinByEntryCode(Long userId, String entryCode) {
        Session session = sessionRepository.findWithPresenterByEntryCode(entryCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ENTRY_CODE));
        return join(session, userId);
    }

    private SessionParticipant join(Session session, Long userId) {
        if (session.isPresenter(userId)) throw new BusinessException(ErrorCode.PRESENTER_CANNOT_JOIN);
        if (session.getStatus() == SessionStatus.ENDED) throw new BusinessException(ErrorCode.SESSION_ENDED);
        return participantRepository.findBySession_IdAndUser_Id(session.getId(), userId)
                .orElseGet(() -> participantRepository.save(new SessionParticipant(session, userService.getUser(userId))));
    }

    @Transactional
    public SessionResponse start(Long userId, Long sessionId) {
        Session session = findOwnedSession(userId, sessionId);
        session.start();
        publishStatusChanged(session);
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse end(Long userId, Long sessionId) {
        Session session = findOwnedSession(userId, sessionId);
        session.end();
        publishStatusChanged(session);
        summaryService.requestFor(session);  // P1: 커밋 후 AI 요약 시작 (결과는 GET /sessions/{id}/summary)
        return SessionResponse.from(session);
    }

    /** 커밋된 뒤 /topic/sessions/{id} 구독자에게 SESSION_STATUS_CHANGED 알림이 나간다 (RealtimeBroadcaster) */
    private void publishStatusChanged(Session session) {
        eventPublisher.publishEvent(new SessionTopicEvent(session.getId(), RealtimeEventType.SESSION_STATUS_CHANGED,
                Map.of("status", session.getStatus().name())));
    }

    /** 다른 도메인(자료, 질문 등)에서 세션 엔티티가 필요할 때 이 메서드를 쓰면 된다. */
    public Session findSession(Long sessionId) {
        return sessionRepository.findWithPresenterById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    private Session findOwnedSession(Long userId, Long sessionId) {
        Session session = findSession(sessionId);
        if (!session.isPresenter(userId)) {
            throw new BusinessException(ErrorCode.NOT_SESSION_PRESENTER);
        }
        return session;
    }

    private String newEntryCode() {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String code = entryCodeGenerator.generate();
            if (!sessionRepository.existsByEntryCode(code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}
