package com.jinhyoung.salary.dev;

import com.jinhyoung.salary.auth.JwtProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 전용 dev 로그인(VERIFY-notion-match). {@code dev} 프로필에서만 등록되며, {@code /api/v1/auth/**}는
 * SecurityConfig 공개 경로라 토큰 없이 호출된다. OAuth 자격증명 없이 노션 시드 사용자로 JWT를 발급해
 * 브라우저에서 폭포·체크리스트 라이브 대조(노션 숫자 일치 확인)를 가능케 한다.
 *
 * <p>경로 {@code /api/v1/auth/dev/login}은 AuthController의 {@code /{provider}}(단일 세그먼트)와 겹치지 않는다.
 * 운영 프로필엔 이 빈이 없으므로 엔드포인트 자체가 존재하지 않는다(노출 위험 없음).
 */
@RestController
@RequestMapping("/api/v1/auth/dev")
@Profile("dev")
public class DevAuthController {

    private final DevSeedService devSeedService;
    private final JwtProvider jwtProvider;

    public DevAuthController(DevSeedService devSeedService, JwtProvider jwtProvider) {
        this.devSeedService = devSeedService;
        this.jwtProvider = jwtProvider;
    }

    /** 노션 시드 사용자를 보장한 뒤 JWT를 발급한다. 응답 형태는 OAuth 로그인(AuthResponse)과 동일. */
    @PostMapping("/login")
    public DevLoginResponse login() {
        long userId = devSeedService.ensureNotionUser();
        return new DevLoginResponse(jwtProvider.issue(userId), false);
    }

    public record DevLoginResponse(String accessToken, boolean isNewUser) {}
}
