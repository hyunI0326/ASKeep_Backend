package com.GDGoCSMU.ASKeep.domain.user.controller;

import com.GDGoCSMU.ASKeep.domain.user.dto.UserResponse;
import com.GDGoCSMU.ASKeep.domain.user.service.UserService;
import com.GDGoCSMU.ASKeep.global.common.ApiResponse;
import com.GDGoCSMU.ASKeep.global.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(userService.getMe(loginUser.userId()));
    }
}
