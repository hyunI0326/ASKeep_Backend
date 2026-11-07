package com.GDGoCSMU.ASKeep.domain.user.controller;

import com.GDGoCSMU.ASKeep.domain.user.dto.ChangePasswordRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.LoginResponse;
import com.GDGoCSMU.ASKeep.domain.user.dto.UpdateProfileRequest;
import com.GDGoCSMU.ASKeep.domain.user.dto.UserResponse;
import com.GDGoCSMU.ASKeep.domain.user.dto.WithdrawRequest;
import com.GDGoCSMU.ASKeep.domain.user.service.UserService;
import com.GDGoCSMU.ASKeep.global.common.ApiResponse;
import com.GDGoCSMU.ASKeep.global.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    /** 이름·학과 변경 (보낸 필드만 변경) */
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateProfile(@AuthenticationPrincipal LoginUser loginUser,
                                                   @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(loginUser.userId(), request));
    }

    /** 비밀번호 변경. 기존 토큰은 모두 무효가 되고 새 토큰을 돌려준다. */
    @PatchMapping("/me/password")
    public ApiResponse<LoginResponse> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                                     @Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok(userService.changePassword(loginUser.userId(), request));
    }

    /** 회원 탈퇴 (본문에 현재 비밀번호 필요) */
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal LoginUser loginUser,
                                      @Valid @RequestBody WithdrawRequest request) {
        userService.withdraw(loginUser.userId(), request.password());
        return ApiResponse.ok();
    }
}
