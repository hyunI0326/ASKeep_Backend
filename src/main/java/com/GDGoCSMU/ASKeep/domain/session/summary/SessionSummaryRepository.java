package com.GDGoCSMU.ASKeep.domain.session.summary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SessionSummaryRepository extends JpaRepository<SessionSummary, Long> {
    Optional<SessionSummary> findBySession_Id(Long sessionId);
    boolean existsBySession_Id(Long sessionId);
    List<SessionSummary> findBySession_IdIn(Collection<Long> sessionIds);
    void deleteBySession_Id(Long sessionId);
}
