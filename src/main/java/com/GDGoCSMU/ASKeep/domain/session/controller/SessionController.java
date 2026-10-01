package com.GDGoCSMU.ASKeep.domain.session.controller;

import com.GDGoCSMU.ASKeep.domain.session.SessionParticipant;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionCreateRequest;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionJoinRequest;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionResponse;
import com.GDGoCSMU.ASKeep.domain.session.dto.SessionUpdateRequest;
import com.GDGoCSMU.ASKeep.domain.session.entity.SessionStatus;
import com.GDGoCSMU.ASKeep.domain.session.service.SessionService;
import com.GDGoCSMU.ASKeep.domain.session.summary.SessionSummaryService;
import com.GDGoCSMU.ASKeep.domain.session.summary.dto.SummaryResponse;
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
    private final SessionSummaryService summaryService;

    public SessionController(SessionService sessionService, SessionSummaryService summaryService) {
        this.sessionService = sessionService;
        this.summaryService = summaryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionResponse> create(@AuthenticationPrincipal LoginUser loginUser,
                                               @Valid @RequestBody SessionCreateRequest request) {
        return ApiResponse.ok(sessionService.create(loginUser.userId(), request));
    }

    /** GET /api/v1/sessions?status=ONGOING 처럼 상태로 거를 수 있다 (생략하면 전체) */
    @GetMapping
    public ApiResponse<List<SessionResponse>> list(@AuthenticationPrincipal LoginUser loginUser,
                                                   @RequestParam(name = "status", required = false) SessionStatus status) {
        return ApiResponse.ok(sessionService.getList(loginUser.userId(), status));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<SessionResponse> detail(@AuthenticationPrincipal LoginUser loginUser,
                                               @PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(sessionService.getDetail(loginUser.userId(), sessionId));
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

    /** 세션 종료 후 AI 요약 (발표자·참여자). 종료 직후엔 PENDING → PROCESSING → COMPLETED/FAILED */
    @GetMapping("/{sessionId}/summary")
    public ApiResponse<SummaryResponse> summary(@AuthenticationPrincipal LoginUser loginUser,
                                                @PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(summaryService.get(loginUser.userId(), sessionId));
    }

    /** 요약이 FAILED일 때 발표자가 다시 요청 */
    @PostMapping("/{sessionId}/summary/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<SummaryResponse> retrySummary(@AuthenticationPrincipal LoginUser loginUser,
                                                     @PathVariable("sessionId") Long sessionId) {
        return ApiResponse.ok(summaryService.retry(loginUser.userId(), sessionId));
    }

    public record ParticipantResponse(Long sessionId, Long userId, String role) {
        static ParticipantResponse from(SessionParticipant p) {
            return new ParticipantResponse(p.getSession().getId(), p.getUser().getId(), p.getRole().name());
        }
    }
}
