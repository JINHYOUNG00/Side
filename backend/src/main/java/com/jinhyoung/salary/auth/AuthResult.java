package com.jinhyoung.salary.auth;

/** 로그인 결과 — 발급된 JWT와 신규 가입 여부(클라이언트는 isNewUser=true면 온보딩으로 라우팅). */
public record AuthResult(String accessToken, boolean isNewUser) {}
