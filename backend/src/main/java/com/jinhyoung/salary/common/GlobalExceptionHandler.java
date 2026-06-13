package com.jinhyoung.salary.common;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    /** 본문 역직렬화 실패(미지원 enum 값·잘못된 날짜 형식 등)도 코드 기반 VALIDATION_FAILED로 통일. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(new ApiErrorResponse(ErrorCode.VALIDATION_FAILED.name(), Map.of()));
    }
}
