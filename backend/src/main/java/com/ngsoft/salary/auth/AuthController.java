package com.ngsoft.salary.auth;

import com.ngsoft.salary.common.ApiException;
import com.ngsoft.salary.common.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 소셜 로그인 엔드포인트 — POST /api/v1/auth/{provider} {code} → {accessToken, isNewUser} (API명세 2장). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/{provider}")
    public AuthResponse login(@PathVariable String provider, @Valid @RequestBody LoginRequest request) {
        OAuthProvider oauthProvider = parseProvider(provider);
        AuthResult result = authService.login(oauthProvider, request.code());
        return new AuthResponse(result.accessToken(), result.isNewUser());
    }

    private static OAuthProvider parseProvider(String raw) {
        try {
            return OAuthProvider.from(raw);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.PROVIDER_NOT_SUPPORTED, Map.of("provider", raw));
        }
    }

    public record LoginRequest(@NotBlank String code) {}

    public record AuthResponse(String accessToken, boolean isNewUser) {}
}
