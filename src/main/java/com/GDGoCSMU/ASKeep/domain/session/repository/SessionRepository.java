package com.GDGoCSMU.ASKeep.domain.session.repository;

import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import com.GDGoCSMU.ASKeep.domain.session.entity.SessionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    boolean existsByEntryCode(String entryCode);

    @EntityGraph(attributePaths = "presenter")
    List<Session> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "presenter")
    List<Session> findAllByStatusOrderByCreatedAtDesc(SessionStatus status);

    @EntityGraph(attributePaths = "presenter")
    Optional<Session> findWithPresenterById(Long id);

    @EntityGraph(attributePaths = "presenter")
    Optional<Session> findWithPresenterByEntryCode(String entryCode);
}
