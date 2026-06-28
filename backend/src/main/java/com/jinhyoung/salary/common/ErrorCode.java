package com.jinhyoung.salary.common;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드(NFR-06). 응답은 문장이 아닌 코드만 — 메시지 조립은 클라이언트 i18n(CLAUDE.md 규칙 7).
 * 각 코드는 HTTP 상태와 1:1로 묶인다.
 */
public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    PROVIDER_NOT_SUPPORTED(HttpStatus.BAD_REQUEST),
    OAUTH_EXCHANGE_FAILED(HttpStatus.BAD_GATEWAY),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    ACCOUNT_LIMIT_EXCEEDED(HttpStatus.CONFLICT),
    ITEM_LIMIT_EXCEEDED(HttpStatus.CONFLICT),
    ENVELOPE_LIMIT_EXCEEDED(HttpStatus.CONFLICT),
    REMINDER_LIMIT_EXCEEDED(HttpStatus.CONFLICT),
    CYCLE_LOCKED(HttpStatus.CONFLICT),
    LINE_LOCKED_BY_SPEND(HttpStatus.CONFLICT),
    CHECK_IN_ALREADY_EXISTS(HttpStatus.CONFLICT),
    SUGGESTION_ALREADY_RESOLVED(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
