package com.GDGoCSMU.ASKeep.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회원가입 필드는 팀에서 확정되면 여기만 수정하면 된다. */
public record SignupRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 30) String name
) {}
