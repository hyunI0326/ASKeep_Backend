package com.GDGoCSMU.ASKeep.domain.session.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH: 보낸 필드만 수정한다 (null이면 그대로 둠) */
public record SessionUpdateRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.") String title
) {}
