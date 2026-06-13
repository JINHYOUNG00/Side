package com.ngsoft.salary.auth;

import java.util.Map;

/**
 * 네이버 /v1/nid/me 응답 정규화 — { "response": { "id": "..", "email": "..", "nickname": ".." } }.
 * 공급자 자체는 비활성(AUTH-02, Phase 7)이나, 활성화 시 즉시 쓰도록 어댑터는 미리 둔다.
 */
public final class NaverAttributesMapper implements OAuthAttributesMapper {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.NAVER;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo map(Map<String, Object> attributes) {
        Object raw = attributes.get("response");
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException("네이버 응답에 response가 없다");
        }
        Map<String, Object> response = (Map<String, Object>) raw;
        Object id = response.get("id");
        if (id == null) {
            throw new IllegalArgumentException("네이버 응답에 id가 없다");
        }
        return new OAuthUserInfo(OAuthProvider.NAVER, String.valueOf(id), (String) response.get("email"), (String)
                response.get("nickname"));
    }
}
