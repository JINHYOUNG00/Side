package com.ngsoft.salary.auth;

import java.util.Locale;

/**
 * 소셜 로그인 공급자. 네이버는 검수 후 활성화(AUTH-02, Phase 7)라 enabled=false.
 * users.provider 컬럼에 이름 그대로 저장된다.
 */
public enum OAuthProvider {
    KAKAO(true),
    GOOGLE(true),
    NAVER(false);

    private final boolean enabled;

    OAuthProvider(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 설정·경로 변수용 소문자 키(kakao/google/naver). app.oauth.{key} 매핑에 사용. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 경로 변수(kakao/google/naver) → enum. 대소문자 무시. 미지원이면 예외. */
    public static OAuthProvider from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("provider가 비어 있다");
        }
        return OAuthProvider.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
