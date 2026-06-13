package com.ngsoft.salary.auth;

import java.util.Objects;

/**
 * 공급자별 응답을 정규화한 사용자 식별 정보. 어댑터(OAuthAttributesMapper)가
 * 카카오/구글/네이버의 서로 다른 JSON 구조를 이 한 형태로 통일한다.
 * email·nickname은 동의 거부 시 null 가능(upsert 단계에서 기본값 처리).
 */
public record OAuthUserInfo(OAuthProvider provider, String providerId, String email, String nickname) {

    public OAuthUserInfo {
        Objects.requireNonNull(provider, "provider");
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId가 비어 있다");
        }
    }
}
