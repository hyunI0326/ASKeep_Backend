package com.GDGoCSMU.ASKeep.domain.session.repository;

import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import com.GDGoCSMU.ASKeep.domain.session.entity.SessionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 내가 만든 세션 */
    @EntityGraph(attributePaths = "presenter")
    List<Session> findAllByPresenter_IdOrderByCreatedAtDesc(Long presenterId);

    /** 내가 참여한 세션 */
    @Query("""
            select s from Session s join fetch s.presenter
            where exists (select p.id from SessionParticipant p where p.session = s and p.user.id = :userId)
            order by s.createdAt desc""")
    List<Session> findJoinedByUserId(@Param("userId") Long userId);
}
