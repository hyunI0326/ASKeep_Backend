package com.GDGoCSMU.ASKeep.domain.user.controller;

import com.GDGoCSMU.ASKeep.domain.user.dto.LoginRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.LoginResponse;
import com.GDGoCSMU.ASKeep.domain.user.dto.PasswordResetConfirmRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.PasswordResetRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.SignupRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.UserResponse;
import com.GDGoCSMU.ASKeep.domain.user.service.AuthService;
import com.GDGoCSMU.ASKeep.domain.user.service.PasswordResetService;
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
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
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

    /** 비밀번호 재설정 1단계: 인증코드 메일 발송 (가입 여부와 관계없이 항상 성공 응답) */
    @PostMapping("/password-reset/request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.request(request.email());
        return ApiResponse.ok();
    }

    /** 비밀번호 재설정 2단계: 인증코드 확인 후 새 비밀번호 저장 */
    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request);
        return ApiResponse.ok();
    }
}
