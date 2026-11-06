package com.GDGoCSMU.ASKeep.domain.user.repository;

import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 이름(username 컬럼)은 DB에서 unique라서 저장 전에 미리 확인한다 */
    boolean existsByName(String name);
}
