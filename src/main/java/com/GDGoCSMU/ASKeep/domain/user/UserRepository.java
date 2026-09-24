package com.GDGoCSMU.ASKeep.domain.user;

import com.GDGoCSMU.ASKeep.domain.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
