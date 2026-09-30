package com.GDGoCSMU.ASKeep.domain.session;


import org.springframework.boot.web.server.servlet.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<StudySession, Long> {
}
