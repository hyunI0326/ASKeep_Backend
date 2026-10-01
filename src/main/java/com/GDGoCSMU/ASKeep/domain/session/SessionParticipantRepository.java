package com.GDGoCSMU.ASKeep.domain.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {
    boolean existsBySession_IdAndUser_Id(Long sessionId, Long userId);
    Optional<SessionParticipant> findBySession_IdAndUser_Id(Long sessionId, Long userId);
    void deleteBySession_IdAndUser_Id(Long sessionId, Long userId);
    java.util.List<SessionParticipant> findBySession_Id(Long sessionId);

    /** 내가 참여한 세션 ID들 (목록에서 입장 코드를 보여줄지 판단용) */
    @org.springframework.data.jpa.repository.Query("select p.session.id from SessionParticipant p where p.user.id = :userId")
    java.util.Set<Long> findSessionIdsByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
