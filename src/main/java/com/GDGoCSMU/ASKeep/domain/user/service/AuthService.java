package com.GDGoCSMU.ASKeep.domain.user.service;

import com.GDGoCSMU.ASKeep.domain.user.dto.LoginRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.LoginResponse;
import com.GDGoCSMU.ASKeep.domain.user.dto.SignupRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.UserResponse;
import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import com.GDGoCSMU.ASKeep.domain.user.repository.UserRepository;
import com.GDGoCSMU.ASKeep.global.exception.BusinessException;
import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import com.GDGoCSMU.ASKeep.global.security.JwtProvider;
import com.GDGoCSMU.ASKeep.global.security.TokenBlacklist;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TokenBlacklist tokenBlacklist;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider, TokenBlacklist tokenBlacklist) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.name().trim());
        return UserResponse.from(userRepository.save(user));
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            // 이메일이 없는 경우와 같은 에러를 줘서 가입 여부가 드러나지 않게 한다
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        String token = jwtProvider.createAccessToken(user.getId(), user.getRole().name());
        return new LoginResponse(token, "Bearer", jwtProvider.getExpirationMillis() / 1000, UserResponse.from(user));
    }

    public void logout(String token) {
        try {
            long expiresAt = jwtProvider.parse(token).getExpiration().getTime();
            tokenBlacklist.add(token, expiresAt);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
