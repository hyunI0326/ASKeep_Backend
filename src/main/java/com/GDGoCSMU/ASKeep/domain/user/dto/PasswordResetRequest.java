package com.GDGoCSMU.ASKeep.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 비밀번호 재설정 1단계: 인증코드를 받을 이메일 */
public record PasswordResetRequest(
        @NotBlank @Email @Size(max = 100) String email
) {}
