package com.GDGoCSMU.ASKeep.domain.answer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByQuestion_IdOrderByIdAsc(Long questionId);
    void deleteByQuestion_Id(Long questionId);
}
