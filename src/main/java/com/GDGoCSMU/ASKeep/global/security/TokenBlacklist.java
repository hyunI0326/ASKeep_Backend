package com.GDGoCSMU.ASKeep.global.security;

import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그아웃된 토큰 목록 (메모리 저장).
 * JWT는 서버가 강제로 만료시킬 수 없어서, 로그아웃한 토큰을 만료 시각까지 여기 보관해 막는다.
 * 서버가 재시작되면 목록이 사라지므로, 배포 단계에서 필요하면 Redis 등으로 바꾸면 된다.
 *
 * 비밀번호 변경·재설정·회원 탈퇴 때는 그 사용자의 "모든" 기존 토큰을 막아야 하므로
 * 사용자별로 "이 시각 이전에 발급된 토큰은 무효"라는 기준 시각도 함께 보관한다.
 */
@Component
public class TokenBlacklist {

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();
    /** userId -> 이 시각(ms) 이전에 발급된 토큰은 무효 */
    private final Map<Long, Long> revokedBefore = new ConcurrentHashMap<>();
    private final long tokenLifetimeMillis;

    public TokenBlacklist(JwtProvider jwtProvider) {
        this.tokenLifetimeMillis = jwtProvider.getExpirationMillis();
    }

    public void add(String token, long expiresAtMillis) {
        tokens.put(token, expiresAtMillis);
        removeExpired();
    }

    public boolean contains(String token) {
        Long expiresAt = tokens.get(token);
        if (expiresAt == null) return false;
        if (expiresAt < System.currentTimeMillis()) {
            tokens.remove(token);
            return false;
        }
        return true;
    }

    /**
     * 지금까지 이 사용자에게 발급된 토큰을 모두 무효로 만든다.
     * 이 호출 이후에 발급되는 토큰(예: 비밀번호 변경 응답의 새 토큰)은 영향을 받지 않는다.
     */
    public void revokeAllOf(Long userId) {
        revokedBefore.put(userId, System.currentTimeMillis());
        removeExpired();
    }

    /** 서명 검증이 끝난 토큰의 Claims로, 사용자 단위로 무효화된 토큰인지 확인한다. */
    public boolean isRevoked(Claims claims) {
        Long before = revokedBefore.get(Long.valueOf(claims.getSubject()));
        if (before == null) return false;
        return issuedAtMillis(claims) < before;
    }

    private long issuedAtMillis(Claims claims) {
        Number millis = claims.get(JwtProvider.ISSUED_AT_MILLIS, Number.class);
        if (millis != null) return millis.longValue();
        // iat_ms가 없는 예전 토큰은 초 단위 iat로 판단한다
        Date issuedAt = claims.getIssuedAt();
        return issuedAt == null ? Long.MIN_VALUE : issuedAt.getTime();
    }

    private void removeExpired() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(e -> e.getValue() < now);
        // 기준 시각 이전 토큰은 이미 전부 만료됐으므로 더 이상 보관할 필요가 없다
        revokedBefore.entrySet().removeIf(e -> e.getValue() + tokenLifetimeMillis < now);
    }
}
