package com.GDGoCSMU.ASKeep.domain.session.controller;

import com.GDGoCSMU.ASKeep.domain.session.dto.MySessionResponse;
import com.GDGoCSMU.ASKeep.domain.session.service.SessionService;
import com.GDGoCSMU.ASKeep.domain.user.domain.UserRole;
import com.GDGoCSMU.ASKeep.global.common.ApiResponse;
import com.GDGoCSMU.ASKeep.global.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/sessions")
public class MySessionController {

    private final SessionService sessionService;

    public MySessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** GET /api/v1/users/me/sessions?role=PRESENTER|AUDIENCE (생략하면 둘 다) */
    @GetMapping
    public ApiResponse<List<MySessionResponse>> mySessions(@AuthenticationPrincipal LoginUser loginUser,
                                                          @RequestParam(name = "role", required = false) UserRole role,
                                                          @RequestParam(name = "tag", required = false) String tag) {
        return ApiResponse.ok(sessionService.getMySessions(loginUser.userId(), role, tag));
    }
}
