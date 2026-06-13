package com.jinhyoung.salary.auth;

import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 유스케이스(AUTH-01, 아키텍처 5.1): 코드 교환 → users upsert → JWT 발급.
 * (provider, provider_id)로 조회·생성하므로 동일 인물의 다른 공급자 계정은 별개 사용자(AUTH-03).
 */
@Service
public class AuthService {

    private final OAuthClient oauthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public AuthService(OAuthClient oauthClient, UserRepository userRepository, JwtProvider jwtProvider) {
        this.oauthClient = oauthClient;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public AuthResult login(OAuthProvider provider, String code) {
        if (!provider.isEnabled()) {
            throw new ApiException(ErrorCode.PROVIDER_NOT_SUPPORTED, Map.of("provider", provider.name()));
        }
        OAuthUserInfo info = oauthClient.fetchUserInfo(provider, code);

        Optional<User> existing = userRepository.findByProviderAndProviderId(provider.name(), info.providerId());
        boolean isNewUser = existing.isEmpty();
        User user = existing.orElseGet(() -> userRepository.save(
                User.createFromOAuth(provider.name(), info.providerId(), info.email(), resolveNickname(info))));

        return new AuthResult(jwtProvider.issue(user.getId()), isNewUser);
    }

    /**
     * nickname은 NOT NULL인데 동의 거부 시 null이 온다 → 이메일 로컬파트 → provider_id 순으로 폴백.
     * UI 문구가 아닌 데이터 기본값이라 하드코딩 금지(규칙 7) 대상이 아니다. 사용자는 온보딩에서 변경 가능.
     */
    private static String resolveNickname(OAuthUserInfo info) {
        if (info.nickname() != null && !info.nickname().isBlank()) {
            return info.nickname();
        }
        String email = info.email();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return info.provider().key() + "_" + info.providerId();
    }
}
