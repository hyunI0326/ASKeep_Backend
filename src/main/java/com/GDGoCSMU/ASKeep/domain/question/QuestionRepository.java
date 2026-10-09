package com.GDGoCSMU.ASKeep.domain.question;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    long countBySession_Id(Long sessionId);
    Page<Question> findBySession_IdOrderByIdDesc(Long sessionId, Pageable pageable);
    List<Question> findBySession_IdAndIdGreaterThanOrderByIdAsc(Long sessionId, Long afterId, Pageable pageable);
    List<Question> findBySession_IdOrderByIdDesc(Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Question q where q.id = :id")
    Optional<Question> findForUpdate(@Param("id") Long id);

    @Query(value = """
            select q from Question q left join q.likedBy liker
            where q.session.id = :sessionId
            group by q order by count(liker) desc, q.id desc""",
            countQuery = "select count(q) from Question q where q.session.id = :sessionId")
    Page<Question> findPopularBySessionId(@Param("sessionId") Long sessionId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("update Question q set q.aiStatus = :status, q.updateAt = :updatedAt where q.id = :id")
    int updateAiStatus(@Param("id") Long id, @Param("status") AiStatus status,
                       @Param("updatedAt") LocalDateTime updatedAt);
}
