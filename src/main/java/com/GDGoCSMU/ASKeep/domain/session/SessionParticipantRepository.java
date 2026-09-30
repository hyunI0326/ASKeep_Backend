package com.GDGoCSMU.ASKeep.domain.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {
    boolean existsBySession_IdAndUser_Id(Long sessionId, Long userId);
    Optional<SessionParticipant> findBySession_IdAndUser_Id(Long sessionId, Long userId);
    void deleteBySession_IdAndUser_Id(Long sessionId, Long userId);
    java.util.List<SessionParticipant> findBySession_Id(Long sessionId);
}
