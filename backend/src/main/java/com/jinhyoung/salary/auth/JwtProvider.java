package com.jinhyoung.salary.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * JWT 발급·검증 (AUTH-01). HS256 대칭키, subject=userId. 리프레시 토큰은 v1 생략(ADR-03).
 * 시각은 주입된 Clock으로만 — LocalDate.now() 등 직접 호출 금지(CLAUDE.md 규칙 3).
 */
public final class JwtProvider {

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JwtProvider(String secret, Duration ttl, Clock clock) {
        // HS256은 최소 256비트(32바이트) 키 요구 — 짧으면 Keys가 예외.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
        this.clock = clock;
    }

    /** userId를 subject로 담은 서명 토큰 발급. iat/exp는 주입된 Clock 기준. */
    public String issue(long userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 서명·만료 검증 후 userId 반환. 위조·만료 시 io.jsonwebtoken 예외를 던진다.
     * 만료 판정도 주입된 Clock 기준(테스트 가능성).
     */
    public long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }
}
