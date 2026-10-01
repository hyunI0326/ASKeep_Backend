package com.GDGoCSMU.ASKeep.domain.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 입장 코드로 세션 참여. 대소문자는 구분하지 않는다 (서버에서 대문자로 바꿔 찾음) */
public record SessionJoinRequest(
        @NotBlank @Pattern(regexp = "^\\s*[A-Za-z0-9]{6}\\s*$", message = "입장 코드는 영문·숫자 6자리입니다.") String entryCode
) {}
