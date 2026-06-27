package com.jinhyoung.salary.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.checkin.infra.CheckIn;
import com.jinhyoung.salary.checkin.infra.CheckInRepository;
import com.jinhyoung.salary.cycle.domain.CycleDefinition;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.suggestion.domain.SuggestionType;
import com.jinhyoung.salary.suggestion.infra.Suggestion;
import com.jinhyoung.salary.suggestion.infra.SuggestionRepository;
import com.jinhyoung.salary.suggestion.infra.SuggestionStatus;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 보정/리밸런싱 제안(SUG-01~03) 통합 테스트. 실 PostgreSQL(Testcontainers) + 가변 KST Clock(규칙 3 주입)으로,
 * 연속 초과/잉여·만기 도래 패턴이 제안으로 영속화되는지(jsonb payload 왕복 포함), 결측 단절·중복 방지(멱등), 그리고
 * GET·반영·닫기 엔드포인트의 소유권·상태 게이트를 결정론적으로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(SuggestionIntegrationTest.MutableClockConfig.class)
class SuggestionIntegrationTest {

    static final class MutableClock extends Clock {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");
        private volatile Instant instant =
                LocalDate.of(2026, 7, 1).atStartOfDay(KST).toInstant();

        void setToday(LocalDate today) {
            this.instant = today.atStartOfDay(KST).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    SuggestionService suggestionService;

    @Autowired
    SuggestionRepository suggestionRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    BudgetItemRepository budgetItemRepository;

    @Autowired
    CycleRepository cycleRepository;

    @Autowired
    CheckInRepository checkInRepository;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void setUp() {
        suggestionRepository.deleteAll();
        checkInRepository.deleteAll();
        budgetItemRepository.deleteAll();
        cycleRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        clock.setToday(LocalDate.of(2026, 7, 1));
    }

    private long newUser(String key) {
        User user = User.createFromOAuth("KAKAO", key, key + "@x.com", key);
        user.updateSettings(2_473_110L, (short) 25, PaydayAdjustment.NONE, null);
        return userRepository.save(user).getId();
    }

    /** 닫힌 사이클 1건 + 그 사이클의 체크인(목표 초과액)을 박는다. overspend>0=초과, <0=잉여. */
    private void closedCycleWithCheckIn(long userId, LocalDate start, LocalDate end, String label, long overspend) {
        long cycleId = cycleRepository
                .save(Cycle.create(userId, new CycleDefinition(start, end, label), 2_473_110))
                .getId();
        long living = overspend < 0 ? -overspend : 0;
        long toppedUp = overspend > 0 ? overspend : 0;
        checkInRepository.save(CheckIn.create(cycleId, living, toppedUp, null));
    }

    /** 닫힌 사이클만(체크인 없음 = 결측). */
    private void closedCycleNoCheckIn(long userId, LocalDate start, LocalDate end, String label) {
        cycleRepository.save(Cycle.create(userId, new CycleDefinition(start, end, label), 2_473_110));
    }

    private long maturingSavingItem(long userId, LocalDate endDate, long amount) {
        long accountId = accountRepository
                .save(Account.create(userId, "케이뱅크", null, null, 0))
                .getId();
        return budgetItemRepository
                .save(BudgetItem.create(
                        userId, accountId, Category.SAVING, "OO적금", amount, LocalDate.of(2025, 7, 1), endDate, null, 0))
                .getId();
    }

    private String auth(long userId) {
        return "Bearer " + jwtProvider.issue(userId);
    }

    // ── 생성(룰 → 영속화) ────────────────────────────────────────────

    @Test
    void 연속_초과면_RAISE_LIVING_제안을_생성한다() {
        long userId = newUser("over");
        // 최신→과거: 12,000 / 15,000 / 18,000 → 평균 15,000 → 올림 20,000.
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "2026-06", 12_000);
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "2026-05", 15_000);
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "2026-04", 18_000);

        assertThat(suggestionService.generateForUser(userId)).isEqualTo(1);

        Suggestion saved = suggestionRepository.findAll().get(0);
        assertThat(saved.getType()).isEqualTo(SuggestionType.RAISE_LIVING);
        assertThat(saved.getStatus()).isEqualTo(SuggestionStatus.PENDING);
        // jsonb 왕복: 숫자는 Number로 돌아오므로 longValue로 비교.
        assertThat(((Number) saved.getPayload().get("suggestedIncrease")).longValue())
                .isEqualTo(20_000L);
    }

    @Test
    void 연속_잉여면_RAISE_SAVING_제안을_생성한다() {
        long userId = newUser("surplus");
        // 잉여(=−overspend) 35,000 / 45,000 / 40,000 → 평균 40,000 → 내림 40,000.
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "2026-06", -35_000);
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "2026-05", -45_000);
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "2026-04", -40_000);

        assertThat(suggestionService.generateForUser(userId)).isEqualTo(1);

        Suggestion saved = suggestionRepository.findAll().get(0);
        assertThat(saved.getType()).isEqualTo(SuggestionType.RAISE_SAVING);
        assertThat(((Number) saved.getPayload().get("suggestedIncrease")).longValue())
                .isEqualTo(40_000L);
    }

    @Test
    void 만기_도래_항목은_REBALANCE_MATURITY_제안을_생성한다() {
        long userId = newUser("maturity");
        // 만기 2026-07-15 → 30일 전(2026-06-15) 도래, today=2026-07-01.
        long itemId = maturingSavingItem(userId, LocalDate.of(2026, 7, 15), 300_000);

        assertThat(suggestionService.generateForUser(userId)).isEqualTo(1);

        Suggestion saved = suggestionRepository.findAll().get(0);
        assertThat(saved.getType()).isEqualTo(SuggestionType.REBALANCE_MATURITY);
        assertThat(((Number) saved.getPayload().get("itemId")).longValue()).isEqualTo(itemId);
        assertThat(((Number) saved.getPayload().get("monthlyAmount")).longValue())
                .isEqualTo(300_000L);
        assertThat(saved.getPayload().get("maturityDate")).isEqualTo("2026-07-15");
    }

    @Test
    void 결측이_끼면_단절되어_생성하지_않는다() {
        long userId = newUser("gap");
        // 최신 사이클 체크인 누락(결측) → streak 단절.
        closedCycleNoCheckIn(userId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "2026-06");
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "2026-05", 15_000);
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "2026-04", 18_000);

        assertThat(suggestionService.generateForUser(userId)).isZero();
        assertThat(suggestionRepository.count()).isZero();
    }

    @Test
    void 같은_날_재실행해도_중복_생성하지_않는다() {
        long userId = newUser("idem");
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "2026-06", 12_000);
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "2026-05", 15_000);
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "2026-04", 18_000);

        assertThat(suggestionService.generateForUser(userId)).isEqualTo(1);
        assertThat(suggestionService.generateForUser(userId)).isZero(); // dedup

        assertThat(suggestionRepository.count()).isEqualTo(1);
    }

    @Test
    void 진행_중인_사이클은_결측으로_세지_않는다() {
        long userId = newUser("open");
        // 가장 최근 사이클이 진행 중(cycle_end ≥ today)이라 닫힌 사이클 3개에서 빠진다 — 그 위 3개 초과로 발동.
        cycleRepository.save(Cycle.create(
                userId,
                new CycleDefinition(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 24), "2026-06b"),
                2_473_110));
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 24), "2026-06", 12_000);
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "2026-05", 15_000);
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "2026-04", 18_000);

        assertThat(suggestionService.generateForUser(userId)).isEqualTo(1);
        assertThat(suggestionRepository.findAll().get(0).getType()).isEqualTo(SuggestionType.RAISE_LIVING);
    }

    @Test
    void 일일_배치는_온보딩한_사용자에게_제안을_생성한다() {
        long userId = newUser("batch");
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "2026-06", 12_000);
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "2026-05", 15_000);
        closedCycleWithCheckIn(userId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "2026-04", 18_000);
        // 온보딩 전(base_income=0) 사용자는 제외 — 사이클이 있어도 findByBaseIncomeGreaterThan에서 빠진다.
        userRepository.save(User.createFromOAuth("KAKAO", "newbie", "newbie@x.com", "newbie"));

        assertThat(suggestionService.generateDailySuggestions()).isEqualTo(1);
        assertThat(suggestionRepository.count()).isEqualTo(1);
    }

    // ── 조회·반영·닫기 엔드포인트 ────────────────────────────────────

    @Test
    void GET_제안목록은_PENDING만_반환한다() throws Exception {
        long userId = newUser("list");
        Suggestion pending = suggestionRepository.save(
                Suggestion.create(userId, SuggestionType.RAISE_LIVING, java.util.Map.of("suggestedIncrease", 20_000L)));

        mockMvc.perform(get("/api/v1/suggestions").header(HttpHeaders.AUTHORIZATION, auth(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(pending.getId()))
                .andExpect(jsonPath("$[0].type").value("RAISE_LIVING"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].payload.suggestedIncrease").value(20_000));
    }

    @Test
    void 제안_반영은_APPLIED로_전이한다() throws Exception {
        long userId = newUser("apply");
        long id = suggestionRepository
                .save(Suggestion.create(
                        userId, SuggestionType.RAISE_SAVING, java.util.Map.of("suggestedIncrease", 30_000L)))
                .getId();

        mockMvc.perform(post("/api/v1/suggestions/" + id + "/apply").header(HttpHeaders.AUTHORIZATION, auth(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        Suggestion reloaded = suggestionRepository.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SuggestionStatus.APPLIED);
        assertThat(reloaded.getResolvedAt()).isNotNull();
    }

    @Test
    void 제안_닫기는_DISMISSED로_전이한다() throws Exception {
        long userId = newUser("dismiss");
        long id = suggestionRepository
                .save(Suggestion.create(
                        userId, SuggestionType.RAISE_LIVING, java.util.Map.of("suggestedIncrease", 20_000L)))
                .getId();

        mockMvc.perform(post("/api/v1/suggestions/" + id + "/dismiss").header(HttpHeaders.AUTHORIZATION, auth(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));

        assertThat(suggestionRepository.findById(id).orElseThrow().getStatus()).isEqualTo(SuggestionStatus.DISMISSED);
    }

    @Test
    void 이미_해소된_제안을_다시_반영하면_409다() throws Exception {
        long userId = newUser("twice");
        long id = suggestionRepository
                .save(Suggestion.create(
                        userId, SuggestionType.RAISE_LIVING, java.util.Map.of("suggestedIncrease", 20_000L)))
                .getId();

        mockMvc.perform(post("/api/v1/suggestions/" + id + "/apply").header(HttpHeaders.AUTHORIZATION, auth(userId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/suggestions/" + id + "/apply").header(HttpHeaders.AUTHORIZATION, auth(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUGGESTION_ALREADY_RESOLVED"));
    }

    @Test
    void 타인의_제안은_반영할_수_없다() throws Exception {
        long ownerId = newUser("owner");
        long otherId = newUser("other");
        long id = suggestionRepository
                .save(Suggestion.create(
                        ownerId, SuggestionType.RAISE_LIVING, java.util.Map.of("suggestedIncrease", 20_000L)))
                .getId();

        mockMvc.perform(post("/api/v1/suggestions/" + id + "/apply").header(HttpHeaders.AUTHORIZATION, auth(otherId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(suggestionRepository.findById(id).orElseThrow().getStatus()).isEqualTo(SuggestionStatus.PENDING);
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/suggestions")).andExpect(status().isUnauthorized());
    }
}
