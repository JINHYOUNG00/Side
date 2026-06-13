package com.jinhyoung.salary.auth;

import java.util.Map;

/**
 * 카카오 /v2/user/me 응답 정규화.
 * { "id": 123, "kakao_account": { "email": "..", "profile": { "nickname": ".." } } }
 */
public final class KakaoAttributesMapper implements OAuthAttributesMapper {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo map(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        if (id == null) {
            throw new IllegalArgumentException("카카오 응답에 id가 없다");
        }
        Map<String, Object> account = asMap(attributes.get("kakao_account"));
        Map<String, Object> profile = account == null ? null : asMap(account.get("profile"));
        String email = account == null ? null : (String) account.get("email");
        String nickname = profile == null ? null : (String) profile.get("nickname");
        return new OAuthUserInfo(OAuthProvider.KAKAO, String.valueOf(id), email, nickname);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }
}
