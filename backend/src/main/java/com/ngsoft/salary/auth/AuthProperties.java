package com.ngsoft.salary.auth;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 설정(application.yml의 {@code app.*}). 시크릿은 환경변수로 주입, 저장소엔 dev 기본값만.
 *
 * @param jwt   JWT 서명 시크릿(HS256, 32바이트 이상)과 만료
 * @param oauth 공급자별 OAuth 엔드포인트·클라이언트(키: kakao/google). 시크릿 미주입 시 빈 문자열
 */
@ConfigurationProperties("app")
public record AuthProperties(Jwt jwt, Map<String, Registration> oauth) {

    public record Jwt(String secret, Duration ttl) {}

    public record Registration(
            String tokenUri, String userInfoUri, String clientId, String clientSecret, String redirectUri) {}
}
