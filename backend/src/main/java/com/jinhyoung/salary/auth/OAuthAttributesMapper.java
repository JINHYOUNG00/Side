package com.jinhyoung.salary.auth;

import java.util.Map;

/**
 * 공급자 userinfo 응답(Map으로 파싱된 JSON)을 OAuthUserInfo로 정규화하는 어댑터.
 * 순수 변환 — HTTP·프레임워크 의존 없음(단위 테스트 대상).
 */
public interface OAuthAttributesMapper {

    OAuthProvider provider();

    OAuthUserInfo map(Map<String, Object> attributes);
}
