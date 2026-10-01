package com.GDGoCSMU.ASKeep.domain.question;

import jakarta.validation.constraints.NotBlank;

public final class QuestionRequests {
    private QuestionRequests() {}
    /** anonymous는 생략 가능 (생략하면 false, api_secp 명세) */
    public record Create(@NotBlank String content, Boolean anonymous) {
        public boolean isAnonymous() { return Boolean.TRUE.equals(anonymous); }
    }
    public record Update(String content, Boolean anonymous) {}
}
