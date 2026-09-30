package com.GDGoCSMU.ASKeep.domain.question;

import jakarta.validation.constraints.NotBlank;

public final class QuestionRequests {
    private QuestionRequests() {}
    public record Create(@NotBlank String content, boolean anonymous) {}
    public record Update(String content, Boolean anonymous) {}
}
