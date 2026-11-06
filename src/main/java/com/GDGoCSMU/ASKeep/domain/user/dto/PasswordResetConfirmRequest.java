package com.GDGoCSMU.ASKeep.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 비밀번호 재설정 2단계: 메일로 받은 6자리 코드와 새 비밀번호 */
public record PasswordResetConfirmRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "6자리 숫자여야 합니다.") String code,
        @NotBlank @Size(min = 8, max = 64) String newPassword
) {}
