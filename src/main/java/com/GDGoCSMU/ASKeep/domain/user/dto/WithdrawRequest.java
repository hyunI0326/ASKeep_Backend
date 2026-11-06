package com.GDGoCSMU.ASKeep.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 회원 탈퇴. 본인 확인을 위해 현재 비밀번호를 다시 받는다. */
public record WithdrawRequest(
        @NotBlank String password
) {}
