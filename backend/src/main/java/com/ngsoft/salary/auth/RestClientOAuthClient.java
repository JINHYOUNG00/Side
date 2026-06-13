package com.ngsoft.salary.auth;

import com.ngsoft.salary.common.ApiException;
import com.ngsoft.salary.common.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 실제 OAuth 코드 교환(아키텍처 5.1): authorization code → access token → userinfo → 정규화.
 * 공급자별 엔드포인트·클라이언트는 {@link AuthProperties}, JSON→OAuthUserInfo 변환은 어댑터에 위임.
 */
public class RestClientOAuthClient implements OAuthClient {

    private final AuthProperties properties;
    private final Map<OAuthProvider, OAuthAttributesMapper> mappers;
    private final RestClient restClient;

    public RestClientOAuthClient(
            AuthProperties properties, List<OAuthAttributesMapper> mappers, RestClient restClient) {
        this.properties = properties;
        this.mappers = mappers.stream().collect(Collectors.toMap(OAuthAttributesMapper::provider, Function.identity()));
        this.restClient = restClient;
    }

    @Override
    public OAuthUserInfo fetchUserInfo(OAuthProvider provider, String authorizationCode) {
        AuthProperties.Registration reg = registration(provider);
        try {
            String accessToken = exchangeCodeForToken(reg, authorizationCode);
            Map<String, Object> attributes = fetchAttributes(reg, accessToken);
            return mapper(provider).map(attributes);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.OAUTH_EXCHANGE_FAILED, Map.of("provider", provider.name()));
        }
    }

    private String exchangeCodeForToken(AuthProperties.Registration reg, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", reg.clientId());
        form.add("client_secret", reg.clientSecret());
        form.add("redirect_uri", reg.redirectUri());

        Map<String, Object> token = restClient
                .post()
                .uri(reg.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP_TYPE);
        Object accessToken = token == null ? null : token.get("access_token");
        if (accessToken == null) {
            throw new IllegalArgumentException("토큰 응답에 access_token이 없다");
        }
        return accessToken.toString();
    }

    private Map<String, Object> fetchAttributes(AuthProperties.Registration reg, String accessToken) {
        Map<String, Object> attributes = restClient
                .get()
                .uri(reg.userInfoUri())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(MAP_TYPE);
        if (attributes == null) {
            throw new IllegalArgumentException("userinfo 응답이 비어 있다");
        }
        return attributes;
    }

    private AuthProperties.Registration registration(OAuthProvider provider) {
        AuthProperties.Registration reg =
                properties.oauth() == null ? null : properties.oauth().get(provider.key());
        if (reg == null) {
            throw new ApiException(ErrorCode.PROVIDER_NOT_SUPPORTED, Map.of("provider", provider.name()));
        }
        return reg;
    }

    private OAuthAttributesMapper mapper(OAuthProvider provider) {
        OAuthAttributesMapper mapper = mappers.get(provider);
        if (mapper == null) {
            throw new ApiException(ErrorCode.PROVIDER_NOT_SUPPORTED, Map.of("provider", provider.name()));
        }
        return mapper;
    }

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new org.springframework.core.ParameterizedTypeReference<>() {};
}
