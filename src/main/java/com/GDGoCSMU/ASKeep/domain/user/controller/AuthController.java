package com.GDGoCSMU.ASKeep.domain.user.controller;

import com.GDGoCSMU.ASKeep.domain.user.dto.LoginRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.LoginResponse;
import com.GDGoCSMU.ASKeep.domain.user.dto.SignupRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.UserResponse;
import com.GDGoCSMU.ASKeep.domain.user.service.AuthService;
import com.GDGoCSMU.ASKeep.global.common.ApiResponse;
import com.GDGoCSMU.ASKeep.global.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(JwtAuthenticationFilter.resolveToken(request));
        return ApiResponse.ok();
    }
}
