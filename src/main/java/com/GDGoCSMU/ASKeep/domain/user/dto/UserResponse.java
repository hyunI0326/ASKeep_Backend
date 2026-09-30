package com.GDGoCSMU.ASKeep.domain.user.dto;

import com.GDGoCSMU.ASKeep.domain.user.entity.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long userId,
        String email,
        String name,
        String role,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(),
                user.getRole().name(), user.getCreatedAt());
    }
}
