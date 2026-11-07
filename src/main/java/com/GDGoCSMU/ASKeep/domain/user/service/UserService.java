package com.GDGoCSMU.ASKeep.domain.user.service;

import com.GDGoCSMU.ASKeep.domain.session.entity.SessionStatus;
import com.GDGoCSMU.ASKeep.domain.session.repository.SessionRepository;
import com.GDGoCSMU.ASKeep.domain.user.dto.ChangePasswordRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.LoginResponse;
import com.GDGoCSMU.ASKeep.domain.user.dto.UpdateProfileRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.UserResponse;
import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import com.GDGoCSMU.ASKeep.domain.user.repository.UserRepository;
import com.GDGoCSMU.ASKeep.global.exception.BusinessException;
import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import com.GDGoCSMU.ASKeep.global.security.JwtProvider;
import com.GDGoCSMU.ASKeep.global.security.TokenBlacklist;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TokenBlacklist tokenBlacklist;

    public UserService(UserRepository userRepository, SessionRepository sessionRepository,
                       PasswordEncoder passwordEncoder, JwtProvider jwtProvider, TokenBlacklist tokenBlacklist) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.tokenBlacklist = tokenBlacklist;
    }

    public UserResponse getMe(Long userId) {
        return UserResponse.from(getUser(userId));
    }

    /** 다른 도메인(질문, 답변 등)에서 사용자 엔티티가 필요할 때 이 메서드를 쓰면 된다. */
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** 이름·학과 변경. 보낸 필드만 바꾼다. */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        if (request.name() == null && request.department() == null) {
            throw new BusinessException(ErrorCode.EMPTY_PROFILE_UPDATE);
        }
        User user = getUser(userId);
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) throw new BusinessException(ErrorCode.INVALID_INPUT);
            if (!name.equals(user.getName())) {
                validateNewName(name, userRepository);
                user.changeName(name);
            }
        }
        if (request.department() != null) {
            String department = request.department().trim();
            if (department.isEmpty()) throw new BusinessException(ErrorCode.INVALID_INPUT);
            user.changeDepartment(department);
        }
        // 이름 unique 위반을 여기서 바로 잡기 위해 flush
        userRepository.flush();
        return UserResponse.from(user);
    }

    /**
     * 비밀번호 변경. 다른 기기에 남아 있는 기존 토큰은 모두 무효가 되고,
     * 지금 쓰는 기기는 로그아웃되지 않도록 새 토큰을 돌려준다.
     */
    @Transactional
    public LoginResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.SAME_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        tokenBlacklist.revokeAllOf(user.getId());
        String token = jwtProvider.createAccessToken(user.getId(), user.getRole().name());
        return new LoginResponse(token, "Bearer", jwtProvider.getExpirationMillis() / 1000, UserResponse.from(user));
    }

    /**
     * 회원 탈퇴. 개인정보(이메일·이름·학과·비밀번호)를 지우고 탈퇴 처리한다.
     * 이 사용자가 만든 세션과 남긴 질문은 다른 참여자의 기록이기도 해서 지우지 않고 "탈퇴한 사용자#id"로 보인다.
     */
    @Transactional
    public void withdraw(Long userId, String password) {
        User user = getUser(userId);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        if (sessionRepository.existsByPresenter_IdAndStatusIn(userId,
                EnumSet.of(SessionStatus.ONGOING, SessionStatus.ACTIVE))) {
            throw new BusinessException(ErrorCode.HOSTING_LIVE_SESSION);
        }
        // 아무도 알 수 없는 비밀번호로 바꿔서 다시 로그인할 수 없게 한다
        user.withdraw(passwordEncoder.encode(UUID.randomUUID().toString()));
        tokenBlacklist.revokeAllOf(userId);
    }

    /** 새로 쓰려는 이름이 예약어가 아니고 다른 사람이 쓰고 있지 않은지 확인한다. (회원가입에서도 사용) */
    static void validateNewName(String name, UserRepository userRepository) {
        if (name.startsWith(User.WITHDRAWN_NAME_PREFIX)) {
            throw new BusinessException(ErrorCode.RESERVED_NAME);
        }
        if (userRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
    }
}
