package com.ngsoft.salary.auth;

/**
 * 인가 코드 → 공급자 사용자 정보 조회 포트(아키텍처 5.1). 구현은 infra(RestClient), 테스트는 스텁으로 교체.
 */
public interface OAuthClient {

    /** authorization code를 토큰으로 교환하고 userinfo를 정규화해 반환. 실패 시 ApiException(OAUTH_EXCHANGE_FAILED). */
    OAuthUserInfo fetchUserInfo(OAuthProvider provider, String authorizationCode);
}
