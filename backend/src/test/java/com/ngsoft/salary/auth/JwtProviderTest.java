package com.ngsoft.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** JWT 발급·검증 단위 테스트 — Clock 주입으로 만료를 결정론적으로 검증(DB 불필요). */
class JwtProviderTest {

    private static final String SECRET = "test-secret-key-which-is-at-least-32-bytes-long!!";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant T0 = Instant.parse("2026-06-13T00:00:00Z");

    @Test
    void 발급한_토큰에서_userId를_복원한다() {
        JwtProvider jwt = new JwtProvider(SECRET, Duration.ofHours(1), Clock.fixed(T0, KST));

        String token = jwt.issue(7L);

        assertThat(jwt.parseUserId(token)).isEqualTo(7L);
    }

    @Test
    void 만료된_토큰은_거부한다() {
        Clock issueClock = Clock.fixed(T0, KST);
        Clock laterClock = Clock.fixed(T0.plus(Duration.ofHours(2)), KST);
        String token = new JwtProvider(SECRET, Duration.ofHours(1), issueClock).issue(7L);

        JwtProvider afterExpiry = new JwtProvider(SECRET, Duration.ofHours(1), laterClock);

        assertThatThrownBy(() -> afterExpiry.parseUserId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void 다른_키로_서명된_토큰은_거부한다() {
        String token = new JwtProvider(SECRET, Duration.ofHours(1), Clock.fixed(T0, KST)).issue(7L);

        JwtProvider otherKey = new JwtProvider(
                "another-secret-key-also-32-bytes-long-xxxxx!", Duration.ofHours(1), Clock.fixed(T0, KST));

        assertThatThrownBy(() -> otherKey.parseUserId(token)).isInstanceOf(JwtException.class);
    }
}
