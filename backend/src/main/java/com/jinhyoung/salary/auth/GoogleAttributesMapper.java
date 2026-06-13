package com.jinhyoung.salary.auth;

import java.util.Map;

/**
 * 구글 OIDC userinfo 응답 정규화.
 * { "sub": "..", "email": "..", "name": ".." }
 */
public final class GoogleAttributesMapper implements OAuthAttributesMapper {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public OAuthUserInfo map(Map<String, Object> attributes) {
        Object sub = attributes.get("sub");
        if (sub == null) {
            throw new IllegalArgumentException("구글 응답에 sub가 없다");
        }
        return new OAuthUserInfo(OAuthProvider.GOOGLE, String.valueOf(sub), (String) attributes.get("email"), (String)
                attributes.get("name"));
    }
}
