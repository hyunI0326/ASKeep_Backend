package com.GDGoCSMU.ASKeep.domain.question;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    long countBySession_Id(Long sessionId);
    Page<Question> findBySession_IdOrderByIdDesc(Long sessionId, Pageable pageable);
    List<Question> findBySession_IdAndIdGreaterThanOrderByIdAsc(Long sessionId, Long afterId, Pageable pageable);
    List<Question> findBySession_IdOrderByIdDesc(Long sessionId);
}
