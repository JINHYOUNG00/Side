package com.ngsoft.salary.common;

import java.util.Map;

/** 오류 응답 본문 — {@code { "code": "UNAUTHORIZED", "params": {...} }} (API명세 1장). */
public record ApiErrorResponse(String code, Map<String, Object> params) {}
