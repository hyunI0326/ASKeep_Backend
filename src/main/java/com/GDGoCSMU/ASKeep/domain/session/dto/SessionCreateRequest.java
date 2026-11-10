package com.GDGoCSMU.ASKeep.domain.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * scheduledStartAt, scheduledEndAt: 세션 예정 시작/종료 일시 (선택)
 * 형식 예: "2026-11-20T14:00:00+09:00"
 */
public record SessionCreateRequest(
        @NotBlank @Size(max = 100) String title,
        String description,
        LocalDateTime scheduledStartAt,
        LocalDateTime scheduledEndAt
) {}