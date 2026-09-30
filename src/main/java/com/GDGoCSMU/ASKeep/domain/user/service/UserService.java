package com.GDGoCSMU.ASKeep.domain.user.service;

import com.GDGoCSMU.ASKeep.domain.user.dto.UserResponse;
import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import com.GDGoCSMU.ASKeep.domain.user.repository.UserRepository;
import com.GDGoCSMU.ASKeep.global.exception.BusinessException;
import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse getMe(Long userId) {
        return UserResponse.from(getUser(userId));
    }

    /** 다른 도메인(질문, 답변 등)에서 사용자 엔티티가 필요할 때 이 메서드를 쓰면 된다. */
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
