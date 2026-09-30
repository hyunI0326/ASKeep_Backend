package com.GDGoCSMU.ASKeep.domain.session;

import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import com.GDGoCSMU.ASKeep.domain.session.repository.SessionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionAccessService {
    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;

    public Session get(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("세션을 찾을 수 없습니다."));
    }

    public Session requireHost(Long sessionId, Long userId) {
        Session session = get(sessionId);
        if (!session.isPresenter(userId)) throw new AccessDeniedException("세션 host만 요청할 수 있습니다.");
        return session;
    }

    public Session requireMember(Long sessionId, Long userId) {
        Session session = get(sessionId);
        if (!session.isPresenter(userId) && !participantRepository.existsBySession_IdAndUser_Id(sessionId, userId)) {
            throw new AccessDeniedException("세션 host 또는 참여자만 요청할 수 있습니다.");
        }
        return session;
    }
}
