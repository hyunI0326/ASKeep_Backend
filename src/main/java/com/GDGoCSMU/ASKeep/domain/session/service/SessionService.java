package com.GDGoCSMU.ASKeep.domain.session.service;

import com.GDGoCSMU.ASKeep.domain.session.dto.SessionCreateRequest;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionResponse;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionUpdateRequest;
import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import com.GDGoCSMU.ASKeep.domain.session.entity.SessionStatus;
import com.GDGoCSMU.ASKeep.domain.session.repository.SessionRepository;
import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import com.GDGoCSMU.ASKeep.domain.user.service.UserService;
import com.GDGoCSMU.ASKeep.global.exception.BusinessException;
import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SessionService {

    private static final int MAX_CODE_ATTEMPTS = 10;

    private final SessionRepository sessionRepository;
    private final UserService userService;
    private final EntryCodeGenerator entryCodeGenerator;

    public SessionService(SessionRepository sessionRepository, UserService userService,
                          EntryCodeGenerator entryCodeGenerator) {
        this.sessionRepository = sessionRepository;
        this.userService = userService;
        this.entryCodeGenerator = entryCodeGenerator;
    }

    @Transactional
    public SessionResponse create(Long userId, SessionCreateRequest request) {
        User presenter = userService.getUser(userId);
        Session session = new Session(request.title().trim(), presenter, newEntryCode());
        return SessionResponse.from(sessionRepository.save(session));
    }

    public List<SessionResponse> getList(SessionStatus status) {
        List<Session> sessions = (status == null)
                ? sessionRepository.findAllByOrderByCreatedAtDesc()
                : sessionRepository.findAllByStatusOrderByCreatedAtDesc(status);
        return sessions.stream().map(SessionResponse::from).toList();
    }

    public SessionResponse getDetail(Long sessionId) {
        return SessionResponse.from(findSession(sessionId));
    }

    @Transactional
    public SessionResponse update(Long userId, Long sessionId, SessionUpdateRequest request) {
        Session session = findOwnedSession(userId, sessionId);
        if (request.title() != null) {
            session.changeTitle(request.title().trim());
        }
        return SessionResponse.from(session);
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        sessionRepository.delete(findOwnedSession(userId, sessionId));
    }

    @Transactional
    public SessionResponse start(Long userId, Long sessionId) {
        Session session = findOwnedSession(userId, sessionId);
        session.start();
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse end(Long userId, Long sessionId) {
        Session session = findOwnedSession(userId, sessionId);
        session.end();
        return SessionResponse.from(session);
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
