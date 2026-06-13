package com.jinhyoung.salary.common;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 예외 → {code, params} 변환(아키텍처 8장). 문장은 만들지 않는다 — 클라이언트 i18n 조립.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.errorCode().status())
                .body(new ApiErrorResponse(ex.errorCode().name(), ex.params()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(new ApiErrorResponse(ErrorCode.VALIDATION_FAILED.name(), Map.of()));
    }
}
