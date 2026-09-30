package com.GDGoCSMU.ASKeep.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtProvider(
            @Value("${askeep.jwt.secret}") String secret,
            @Value("${askeep.jwt.expiration-seconds:3600}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        if (expirationSeconds < 1) throw new IllegalArgumentException("JWT 만료 시간은 1초 이상이어야 합니다.");
        this.expirationMillis = Math.multiplyExact(expirationSeconds, 1000);
    }

    public String createAccessToken(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    /** 서명·만료를 검증하고 Claims를 돌려준다. 유효하지 않으면 JwtException. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }
}
