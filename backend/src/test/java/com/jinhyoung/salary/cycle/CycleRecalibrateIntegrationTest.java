package com.jinhyoung.salary.cycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 현재 사이클 지급일 재보정({@code POST /cycles/current/recalibrate}) 통합 테스트. 실 PostgreSQL(Testcontainers)
 * + MockMvc + 실 JWT. 이미 만들어진 사이클의 경계가 틀린 월급일로 박혀 있을 때, 바뀐 설정으로 경계를 다시 도출해
 * 이번 사이클을 다시 만드는 동선을 검증한다 — 이체(DONE) 시작 전 사이클만, 확인 실수령액은 보존.
 *
 * <p>기준 데이터는 노션 실데이터(income 2,473,110, payday 25·NONE → 사이클 6/25~7/24). "오늘"은 주입한 KST
 * {@code Clock}(규칙 3)으로 6/28에 고정해, payday를 5로 바꾸면 오늘을 포함하는 사이클이 6/5~7/4로 재도출됨을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(CycleRecalibrateIntegrationTest.MutableClockConfig.class)
class CycleRecalibrateIntegrationTest {

    /** 테스트마다 기준일을 끼워 넣기 위한 가변 KST Clock(now() 직접 호출 대신 주입 — 규칙 3). */
    static final class MutableClock extends Clock {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");
        private volatile Instant instant =
                LocalDate.of(2026, 6, 28).atStartOfDay(KST).toInstant();

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
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    BudgetItemRepository budgetItemRepository;

    @Autowired
    CycleSnapshotService cycleSnapshotService;

    @Autowired
    CycleRepository cycleRepository;

    @Autowired
    PlanLineRepository planLineRepository;

    @BeforeEach
    void clear() {
        planLineRepository.deleteAll();
        cycleRepository.deleteAll();
        budgetItemRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        clock.setToday(LocalDate.of(2026, 6, 28));
    }

    private long newUser(String key) {
        return userRepository
                .save(User.createFromOAuth("KAKAO", key, key + "@x.com", key))
                .getId();
    }

    private long newAccount(long userId, String name, int sortOrder) {
        return accountRepository
                .save(Account.create(userId, name, null, null, sortOrder))
                .getId();
    }

    private void newItem(long userId, long accountId, Category category, String name, long amount, int sortOrder) {
        budgetItemRepository.save(BudgetItem.create(
                userId, accountId, category, name, amount, LocalDate.of(2026, 6, 1), null, null, sortOrder));
    }

    /** 노션 실데이터 6항목 + 생활비 통장 지정. payday는 호출자가 별도로 정한다. */
    private void seedNotion(long userId, int payday) {
        long savings = newAccount(userId, "국민", 0);
        long etc = newAccount(userId, "기타통장", 1);
        long living = newAccount(userId, "생활비통장", 2);
        newItem(userId, savings, Category.SAVING, "청년도약계좌", 700_000, 0);
        newItem(userId, etc, Category.INVESTMENT, "ETF", 800_000, 1);
        newItem(userId, etc, Category.FIXED, "월세", 310_600, 2);
        newItem(userId, etc, Category.INSURANCE, "실손보험", 95_653, 3);
        newItem(userId, etc, Category.SUBSCRIPTION, "넷플릭스", 10_750, 4);
        newItem(userId, etc, Category.EMERGENCY, "비상금", 200_000, 5);
        User user = userRepository.findById(userId).orElseThrow();
        user.updateSettings(2_473_110, (short) payday, PaydayAdjustment.NONE, living);
        userRepository.save(user);
    }

    private void setPayday(long userId, int payday) {
        User user = userRepository.findById(userId).orElseThrow();
        user.updateSettings(
                user.getBaseIncome(), (short) payday, user.getPaydayAdjustment(), user.getLivingAccountId());
        userRepository.save(user);
    }

    private String token(long userId) {
        return "Bearer " + jwtProvider.issue(userId);
    }

    private ResultActions recalibrate(long userId) throws Exception {
        return mockMvc.perform(
                post("/api/v1/cycles/current/recalibrate").header(HttpHeaders.AUTHORIZATION, token(userId)));
    }

    private long lineId(long cycleId, String nameSnapshot) {
        return planLineRepository.findByCycleIdOrderByIdAsc(cycleId).stream()
                .filter(line -> line.getNameSnapshot().equals(nameSnapshot))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void 월급일을_바꾸면_현재_사이클_경계를_재도출해_다시_만든다() throws Exception {
        long userId = newUser("recal");
        seedNotion(userId, 25); // 6/25~7/24 사이클
        Cycle old = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));
        assertThat(old.getCycleStart()).isEqualTo(LocalDate.of(2026, 6, 25));

        // 월급일을 5일로 정정 → 오늘(6/28)을 포함하는 사이클은 6/5~7/4가 맞다.
        setPayday(userId, 5);

        recalibrate(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleStart").value("2026-06-05"))
                .andExpect(jsonPath("$.cycleEnd").value("2026-07-04"))
                .andExpect(jsonPath("$.label").value("2026-06"))
                .andExpect(jsonPath("$.income").value(2_473_110))
                .andExpect(jsonPath("$.incomeConfirmed").value(false));

        // 옛 사이클은 삭제되고 새 경계 사이클 1건만 남는다. plan_lines도 새로 7건 재생성.
        assertThat(cycleRepository.findAll()).hasSize(1);
        Cycle recreated = cycleRepository
                .findByUserIdAndCycleStart(userId, LocalDate.of(2026, 6, 5))
                .orElseThrow();
        assertThat(recreated.getId()).isNotEqualTo(old.getId());
        assertThat(planLineRepository.findByCycleIdOrderByIdAsc(recreated.getId()))
                .hasSize(7);
        assertThat(planLineRepository.findAll()).hasSize(7);
    }

    @Test
    void 확인된_실수령액은_재보정_후에도_보존된다() throws Exception {
        long userId = newUser("keepincome");
        seedNotion(userId, 25);
        Cycle old = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));
        // 체크리스트에서 실수령액을 확인(2,480,000, 평소와 임계 미만 차이라 제안 없음).
        old.confirmIncome(2_480_000);
        cycleRepository.save(old);

        setPayday(userId, 5);

        recalibrate(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleStart").value("2026-06-05"))
                .andExpect(jsonPath("$.income").value(2_480_000))
                .andExpect(jsonPath("$.incomeConfirmed").value(true));
    }

    @Test
    void 경계가_이미_올바르면_멱등으로_같은_사이클을_돌려준다() throws Exception {
        long userId = newUser("idem");
        seedNotion(userId, 25);
        Cycle cycle = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));

        // payday 그대로(25) → 오늘(6/28)을 포함하는 경계가 이미 6/25라 변경 없음.
        recalibrate(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cycle.getId()))
                .andExpect(jsonPath("$.cycleStart").value("2026-06-25"));

        assertThat(cycleRepository.findAll()).hasSize(1);
    }

    @Test
    void 이미_이체된_DONE_라인이_있으면_409로_막고_사이클을_보존한다() throws Exception {
        long userId = newUser("locked");
        seedNotion(userId, 25);
        Cycle cycle = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));
        long etf = lineId(cycle.getId(), "ETF");
        // ETF(ITEM 라인)를 완료로 전이 — 이체 시작.
        mockMvc.perform(patch("/api/v1/plan-lines/" + etf)
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .contentType("application/json")
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk());

        setPayday(userId, 5);

        recalibrate(userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CYCLE_LOCKED"));

        // 사이클·라인 불변 — 옛 경계(6/25) 그대로 유지.
        Cycle unchanged = cycleRepository.findById(cycle.getId()).orElseThrow();
        assertThat(unchanged.getCycleStart()).isEqualTo(LocalDate.of(2026, 6, 25));
        assertThat(planLineRepository.findByCycleIdOrderByIdAsc(cycle.getId())).hasSize(7);
    }

    @Test
    void 현재_사이클이_없으면_404다() throws Exception {
        long userId = newUser("nocycle");
        seedNotion(userId, 25); // 스냅샷은 만들지 않는다 → 오늘 포함 사이클 없음.

        recalibrate(userId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 인증_없으면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/cycles/current/recalibrate")).andExpect(status().isUnauthorized());
    }
}
