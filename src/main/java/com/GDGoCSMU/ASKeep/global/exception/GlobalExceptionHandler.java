package com.GDGoCSMU.ASKeep.global.exception;

import com.GDGoCSMU.ASKeep.global.common.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse(ErrorCode.INVALID_INPUT.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(), "요청 본문 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(), e.getName() + " 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(), e.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(404).body(ApiResponse.fail(ErrorCode.RESOURCE_NOT_FOUND.name(), e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException e) {
        return ResponseEntity.status(403).body(ApiResponse.fail(ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleConflict(RuntimeException e) {
        return ResponseEntity.status(409).body(ApiResponse.fail(ErrorCode.CONFLICT.name(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        // 404, 405 등 스프링이 원래 상태코드를 알고 있는 예외는 그 상태코드 그대로 돌려준다
        if (e instanceof ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode())
                    .body(ApiResponse.fail(er.getStatusCode().toString(), e.getMessage()));
        }
        log.error("Unhandled exception", e);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getMessage()));
    }
}
