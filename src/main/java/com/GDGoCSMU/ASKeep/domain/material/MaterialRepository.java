package com.GDGoCSMU.ASKeep.domain.material;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {
    java.util.List<Material> findBySession_Id(Long sessionId);
    long countBySession_Id(Long sessionId);
    Page<Material> findBySession_IdOrderByIdDesc(Long sessionId, Pageable pageable);
}
