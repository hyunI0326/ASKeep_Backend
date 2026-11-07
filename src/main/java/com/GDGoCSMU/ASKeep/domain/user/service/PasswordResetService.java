package com.GDGoCSMU.ASKeep.domain.user.service;

import com.GDGoCSMU.ASKeep.domain.user.dto.PasswordResetConfirmRequest;
import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import com.GDGoCSMU.ASKeep.domain.user.repository.UserRepository;
import com.GDGoCSMU.ASKeep.global.exception.BusinessException;
import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import com.GDGoCSMU.ASKeep.global.security.TokenBlacklist;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 비밀번호 재설정 (로그인 못 하는 사용자용).
 *
 * 1) request: 이메일로 6자리 인증코드를 보낸다. 가입되지 않은 이메일이어도 똑같이 성공 응답을 줘서
 *    가입 여부가 드러나지 않게 한다.
 * 2) confirm: 이메일 + 코드 + 새 비밀번호로 비밀번호를 바꾸고, 기존 토큰을 모두 무효로 만든다.
 *
 * 코드는 TokenBlacklist처럼 메모리에 보관한다. 서버가 재시작되면 사라지므로 그때는 다시 요청하면 된다.
 */
@Service
@Transactional(readOnly = true)
public class PasswordResetService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    /** 같은 이메일로 다시 보내려면 기다려야 하는 시간 (메일 폭탄 방지) */
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    /** 코드를 이만큼 틀리면 그 코드는 폐기 (6자리 숫자 무작위 대입 방지) */
    static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklist tokenBlacklist;
    private final PasswordResetMailer mailer;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, ResetCode> codes = new ConcurrentHashMap<>();

    public PasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                TokenBlacklist tokenBlacklist, PasswordResetMailer mailer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenBlacklist = tokenBlacklist;
        this.mailer = mailer;
    }

    public void request(String rawEmail) {
        String email = normalize(rawEmail);
        long now = System.currentTimeMillis();
        removeExpired(now);

        ResetCode previous = codes.get(email);
        if (previous != null && now < previous.issuedAt + RESEND_COOLDOWN.toMillis()) {
            return; // 너무 자주 요청하면 조용히 무시 (응답은 똑같이 성공)
        }
        if (userRepository.findByEmail(email).isEmpty()) {
            return; // 가입 여부를 숨기기 위해 똑같이 성공 처리
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        codes.put(email, new ResetCode(code, now, now + CODE_TTL.toMillis()));
        try {
            mailer.send(email, code, CODE_TTL.toMinutes());
        } catch (RuntimeException e) {
            codes.remove(email); // 못 보낸 코드는 지워서 바로 다시 요청할 수 있게 한다
            throw e;
        }
    }

    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        String email = normalize(request.email());
        ResetCode saved = codes.get(email);
        if (saved == null || saved.expiresAt < System.currentTimeMillis()) {
            codes.remove(email);
            throw new BusinessException(ErrorCode.INVALID_RESET_CODE);
        }
        if (!saved.matches(request.code())) {
            if (saved.failedAttempt() >= MAX_ATTEMPTS) codes.remove(email);
            throw new BusinessException(ErrorCode.INVALID_RESET_CODE);
        }
        // 코드는 한 번만 쓸 수 있다
        codes.remove(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_CODE));
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        tokenBlacklist.revokeAllOf(user.getId());
    }

    private void removeExpired(long now) {
        codes.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static final class ResetCode {
        private final String code;
        private final long issuedAt;
        private final long expiresAt;
        private int failedAttempts;

        ResetCode(String code, long issuedAt, long expiresAt) {
            this.code = code;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }

        boolean matches(String input) {
            // 비교 시간으로 코드가 새지 않게 상수 시간 비교
            return MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),
                    input.getBytes(StandardCharsets.UTF_8));
        }

        synchronized int failedAttempt() {
            return ++failedAttempts;
        }
    }
}
