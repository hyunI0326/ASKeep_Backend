package com.GDGoCSMU.ASKeep.domain.session.controller;

import com.GDGoCSMU.ASKeep.domain.session.SessionParticipant;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionCreateRequest;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionJoinRequest;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionResponse;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionUpdateRequest;
import com.GDGoCSMU.ASKeep.domain.session.entity.SessionStatus;
import com.GDGoCSMU.ASKeep.domain.session.service.SessionService;
import com.GDGoCSMU.ASKeep.global.common.ApiResponse;
import com.GDGoCSMU.ASKeep.global.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionResponse> create(@AuthenticationPrincipal LoginUser loginUser,
                                               @Valid @RequestBody SessionCreateRequest request) {
        return ApiResponse.ok(sessionService.create(loginUser.userId(), request));
    }

    /** GET /api/v1/sessions?status=ONGOING 처럼 상태로 거를 수 있다 (생략하면 전체) */
    @GetMapping
    public ApiResponse<List<SessionResponse>> list(@RequestParam(name = "status", required = false) SessionStatus status) {
        return ApiResponse.ok(sessionService.getList(status));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<SessionResponse> detail(@PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(sessionService.getDetail(sessionId));
    }

    @PatchMapping("/{sessionId}")
    public ApiResponse<SessionResponse> update(@AuthenticationPrincipal LoginUser loginUser,
                                               @PathVariable("sessionId") Long sessionId,
                                               @Valid @RequestBody SessionUpdateRequest request) {
        return ApiResponse.ok(sessionService.update(loginUser.userId(), sessionId, request));
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser,
                                    @PathVariable("sessionId") Long sessionId) {
        sessionService.delete(loginUser.userId(), sessionId);
        return ApiResponse.ok();
    }

    @PostMapping("/{sessionId}/start")
    public ApiResponse<SessionResponse> start(@AuthenticationPrincipal LoginUser loginUser,
                                              @PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(sessionService.start(loginUser.userId(), sessionId));
    }

    @PostMapping("/{sessionId}/end")
    public ApiResponse<SessionResponse> end(@AuthenticationPrincipal LoginUser loginUser,
                                              @PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(sessionService.end(loginUser.userId(), sessionId));
    }

    /** POST /api/v1/sessions/participants  {"entryCode":"K7P2QX"} — 입장 코드로 참여 (참여는 이 방법만 허용) */
    @PostMapping("/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ParticipantResponse> joinByEntryCode(@AuthenticationPrincipal LoginUser loginUser,
                                                            @Valid @RequestBody SessionJoinRequest request) {
        return ApiResponse.ok(ParticipantResponse.from(sessionService.joinByEntryCode(loginUser.userId(), request.entryCode())));
    }

    public record ParticipantResponse(Long sessionId, Long userId, String role) {
        static ParticipantResponse from(SessionParticipant p) {
            return new ParticipantResponse(p.getSession().getId(), p.getUser().getId(), p.getRole().name());
        }
    }
}
