package com.jinhyoung.salary.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 회원 탈퇴 통합 테스트(AUTH-04). 실 PostgreSQL을 Testcontainers로 띄워 Flyway V1/V2 전체 스키마의
 * {@code ON DELETE CASCADE}가 실제로 동작하는지 검증한다 — 탈퇴 시 전 도메인 물리 삭제(잔존 0건), 타인 데이터
 * 불가침, 멱등, 인증 게이트(401). 탈퇴는 soft delete가 아닌 유일한 물리 삭제 경로(규칙 5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserWithdrawalIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** users에 cascade로 매달린 전 도메인 — 탈퇴 후 본인분은 0건이어야 한다(전역 참조 holidays 제외). */
    private static final List<String> USER_OWNED_TABLES =
            List.of("accounts", "budget_items", "envelopes", "cycles", "suggestions", "notification_logs", "reminders");

    /** user_id가 없는 스냅샷·기록 테이블 — accounts/cycles/envelopes를 거쳐 간접 cascade된다. */
    private static final List<String> CHILD_TABLES = List.of("envelope_transactions", "plan_lines", "check_ins");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtProvider jwtProvider;

    private long aliceId;
    private long bobId;
    private String aliceToken;

    @BeforeEach
    void setUp() {
        // users를 cascade로 비우면 전 도메인이 함께 비워진다(holidays는 사용자 무관이라 유지).
        jdbc.execute("truncate table users restart identity cascade");
        aliceId = newUser("alice");
        bobId = newUser("bob");
        aliceToken = jwtProvider.issue(aliceId);
        seedFullGraph(aliceId);
        seedFullGraph(bobId);
    }

    private long newUser(String key) {
        return userRepository
                .save(User.createFromOAuth("KAKAO", key, key + "@x.com", key))
                .getId();
    }

    /** 한 사용자에 대해 모든 자식 테이블에 1건씩 심는다 — 직접(user_id)·간접(account/cycle/envelope 경유) cascade를 모두 덮는다. */
    private void seedFullGraph(long userId) {
        long accountId =
                insert("insert into accounts(user_id, name, sort_order) values (?, ?, 0) returning id", userId, "통장");
        insert(
                "insert into budget_items(user_id, account_id, category, name, amount, start_date, sort_order, status)"
                        + " values (?, ?, 'SAVING', '적금', 100000, date '2026-01-01', 0, 'ACTIVE') returning id",
                userId,
                accountId);
        long envelopeId = insert(
                "insert into envelopes(user_id, account_id, name, target_amount, next_due_date, status)"
                        + " values (?, ?, '경조사', 500000, date '2026-12-01', 'ACTIVE') returning id",
                userId,
                accountId);
        insert(
                "insert into envelope_transactions(envelope_id, type, amount, occurred_on)"
                        + " values (?, 'DEPOSIT', 50000, date '2026-01-15') returning id",
                envelopeId);
        long cycleId = insert(
                "insert into cycles(user_id, cycle_start, cycle_end, label, income)"
                        + " values (?, date '2026-01-25', date '2026-02-24', '2026-01', 2500000) returning id",
                userId);
        insert(
                "insert into plan_lines(cycle_id, line_type, name_snapshot, category_snapshot, account_name_snapshot,"
                        + " planned_amount, status) values (?, 'ITEM', '적금', 'SAVING', '통장', 100000, 'PENDING')"
                        + " returning id",
                cycleId);
        insert(
                "insert into check_ins(cycle_id, living_remaining, topped_up, overspend) values (?, 30000, 0, -30000)"
                        + " returning id",
                cycleId);
        insert(
                "insert into suggestions(user_id, type, payload, status) values (?, 'RAISE_LIVING', '{}'::jsonb,"
                        + " 'PENDING') returning id",
                userId);
        insert(
                "insert into notification_logs(user_id, type, target_date, channel)"
                        + " values (?, 'PAYDAY', date '2026-01-25', 'EMAIL') returning id",
                userId);
        insert(
                "insert into reminders(user_id, label, interval_months, next_remind_date, status)"
                        + " values (?, '외화 점검', 3, date '2026-04-01', 'ACTIVE') returning id",
                userId);
    }

    private long insert(String sql, Object... args) {
        Long id = jdbc.queryForObject(sql, Long.class, args);
        if (id == null) {
            throw new IllegalStateException("insert returned no id: " + sql);
        }
        return id;
    }

    private long count(String table, String where, Object... args) {
        Long n = jdbc.queryForObject("select count(*) from " + table + " " + where, Long.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void 탈퇴하면_본인의_전_도메인_데이터가_물리_삭제된다_잔존_0건() throws Exception {
        mockMvc.perform(delete("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        // users 본체부터 사라진다.
        assertThat(userRepository.findById(aliceId)).isEmpty();

        // user_id 보유 테이블: 앨리스분 0건.
        for (String table : USER_OWNED_TABLES) {
            assertThat(count(table, "where user_id = ?", aliceId))
                    .as("%s 잔존(user_id=alice)", table)
                    .isZero();
        }

        // user_id 없는 자식 테이블: 앨리스 사이클·봉투를 거쳐 간접 cascade되어 봅의 1건만 남는다.
        for (String table : CHILD_TABLES) {
            assertThat(count(table, "")).as("%s 전역 잔존", table).isEqualTo(1);
        }
    }

    @Test
    void 탈퇴는_타인의_데이터를_건드리지_않는다() throws Exception {
        mockMvc.perform(delete("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        // 봅은 사용자도 전 도메인 데이터도 그대로다.
        assertThat(userRepository.findById(bobId)).isPresent();
        for (String table : USER_OWNED_TABLES) {
            assertThat(count(table, "where user_id = ?", bobId))
                    .as("%s 봅 데이터 보존", table)
                    .isEqualTo(1);
        }
        for (String table : CHILD_TABLES) {
            assertThat(count(table, "")).as("%s 봅 자식 보존", table).isEqualTo(1);
        }
    }

    @Test
    void 이미_탈퇴한_사용자가_다시_호출해도_204다_멱등() throws Exception {
        mockMvc.perform(delete("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        // 토큰은 여전히 유효하지만 users 행은 사라진 상태 — 재호출도 안전하게 204.
        mockMvc.perform(delete("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(bobId)).isPresent();
    }

    @Test
    void 토큰_없이_탈퇴_호출은_401이다() throws Exception {
        mockMvc.perform(delete("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 인증 실패라 아무도 삭제되지 않는다.
        assertThat(userRepository.findById(aliceId)).isPresent();
    }
}
