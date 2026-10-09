package com.GDGoCSMU.ASKeep.domain.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class QuestionRequests {
    private QuestionRequests() {}
    /** anonymous는 생략 가능 (생략하면 false, api_secp 명세) */
    public record Create(@NotBlank String content, Boolean anonymous) {
        public boolean isAnonymous() { return Boolean.TRUE.equals(anonymous); }
    }
    public record Update(String content, Boolean anonymous) {}
    /** 답변 완료 처리: ANSWERED(완료) / OPEN(완료 취소) */
    public record ChangeStatus(@NotNull QuestionStatus status) {}
}