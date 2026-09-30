package com.GDGoCSMU.ASKeep.global.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그아웃된 토큰 목록 (메모리 저장).
 * JWT는 서버가 강제로 만료시킬 수 없어서, 로그아웃한 토큰을 만료 시각까지 여기 보관해 막는다.
 * 서버가 재시작되면 목록이 사라지므로, 배포 단계에서 필요하면 Redis 등으로 바꾸면 된다.
 */
@Component
public class TokenBlacklist {

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

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

    private void removeExpired() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(e -> e.getValue() < now);
    }
}
