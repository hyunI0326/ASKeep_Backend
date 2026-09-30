package com.GDGoCSMU.ASKeep.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 항목을 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청을 현재 상태에서 처리할 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 사용자 / 인증
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 세션
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    NOT_SESSION_PRESENTER(HttpStatus.FORBIDDEN, "세션 발표자만 할 수 있습니다."),
    SESSION_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작되었거나 종료된 세션입니다."),
    SESSION_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 세션이 아닙니다."),
    SESSION_ALREADY_ENDED(HttpStatus.CONFLICT, "종료된 세션은 수정할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
