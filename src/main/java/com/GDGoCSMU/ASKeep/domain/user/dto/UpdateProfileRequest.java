package com.GDGoCSMU.ASKeep.domain.user.dto;

import jakarta.validation.constraints.Size;

/** 내 정보 수정. 바꿀 필드만 보내면 되고, 보내지 않은(null) 필드는 그대로 둔다. */
public record UpdateProfileRequest(
        @Size(max = 30) String name,
        @Size(max = 50) String department
) {}
