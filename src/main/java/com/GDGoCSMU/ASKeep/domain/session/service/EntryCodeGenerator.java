package com.GDGoCSMU.ASKeep.domain.session.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** 세션 입장 코드 (6자리, 헷갈리는 0/O/1/I 제외) */
@Component
public class EntryCodeGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
