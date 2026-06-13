package com.jinhyoung.salary.common;

import java.util.Map;

/**
 * 코드 기반 도메인 예외. {@link GlobalExceptionHandler}가 {code, params}로 직렬화한다.
 * 문장은 담지 않는다 — 클라이언트가 code+params로 i18n 조립(CLAUDE.md 규칙 7).
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, Object> params;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public ApiException(ErrorCode errorCode, Map<String, Object> params) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.params = params == null ? Map.of() : Map.copyOf(params);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> params() {
        return params;
    }
}
