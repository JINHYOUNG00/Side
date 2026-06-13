package com.ngsoft.salary.auth;

import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** 인증 빈 배선 — 어댑터 3종, OAuth 클라이언트, JwtProvider(주입된 KST Clock 사용). */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

    @Bean
    public KakaoAttributesMapper kakaoAttributesMapper() {
        return new KakaoAttributesMapper();
    }

    @Bean
    public GoogleAttributesMapper googleAttributesMapper() {
        return new GoogleAttributesMapper();
    }

    @Bean
    public NaverAttributesMapper naverAttributesMapper() {
        return new NaverAttributesMapper();
    }

    @Bean
    public RestClient oauthRestClient() {
        return RestClient.create();
    }

    @Bean
    public OAuthClient oauthClient(
            AuthProperties properties, List<OAuthAttributesMapper> mappers, RestClient oauthRestClient) {
        return new RestClientOAuthClient(properties, mappers, oauthRestClient);
    }

    @Bean
    public JwtProvider jwtProvider(AuthProperties properties, Clock clock) {
        return new JwtProvider(properties.jwt().secret(), properties.jwt().ttl(), clock);
    }
}
