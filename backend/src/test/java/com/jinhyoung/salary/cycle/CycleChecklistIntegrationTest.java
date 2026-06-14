package com.jinhyoung.salary.cycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.jinhyoung.salary.cycle.infra.PlanLine;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.cycle.infra.PlanLineStatus;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통장별 체크리스트 조회·상태 전이(CYCLE-06) 통합 테스트. 실 PostgreSQL(Testcontainers) + MockMvc + 실 JWT.
 *
 * <p>기준 데이터는 노션 실데이터(income 2,473,110): 통장 3개(국민·기타통장·생활비통장)에 ITEM 6건 + LIVING 1건이
 * 스냅샷된다. 통장별 합은 국민 700,000 / 기타통장 1,417,003 / 생활비통장(LIVING) 356,107 = 2,473,110이다.
 * "오늘"은 사이클 경계에 맞춰 끼울 수 있는 KST {@code Clock}(규칙 3 주입)으로 결정론 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(CycleChecklistIntegrationTest.MutableClockConfig.class)
class CycleChecklistIntegrationTest {

    /** 테스트마다 기준일을 바꿔 끼우기 위한 가변 KST Clock(now() 직접 호출 대신 주입 — 규칙 3). */
    static final class MutableClock extends Clock {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");
        private volatile Instant instant =
                LocalDate.of(2026, 6, 25).atStartOfDay(KST).toInstant();

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
        clock.setToday(LocalDate.of(2026, 6, 25));
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

    /** 노션 실데이터 6항목을 박고 생활비 통장을 지정한다(국민 1건·기타통장 5건·생활비통장 LIVING). */
    private void seedNotion(long userId) {
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
        user.updateSettings(2_473_110, (short) 25, PaydayAdjustment.NONE, living);
        userRepository.save(user);
    }

    private Cycle snapshot(long userId) {
        return cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));
    }

    private String token(long userId) {
        return "Bearer " + jwtProvider.issue(userId);
    }

    private ResultActions getCurrent(long userId) throws Exception {
        return mockMvc.perform(get("/api/v1/cycles/current").header(HttpHeaders.AUTHORIZATION, token(userId)));
    }

    private ResultActions patchLine(long userId, long lineId, String statusValue) throws Exception {
        return mockMvc.perform(patch("/api/v1/plan-lines/" + lineId)
                .header(HttpHeaders.AUTHORIZATION, token(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + statusValue + "\"}"));
    }

    private long lineId(long cycleId, String nameSnapshot) {
        return planLineRepository.findByCycleIdOrderByIdAsc(cycleId).stream()
                .filter(line -> line.getNameSnapshot().equals(nameSnapshot))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void 현재_사이클을_통장별로_묶어_체크리스트와_진행도를_돌려준다() throws Exception {
        long userId = newUser("home");
        seedNotion(userId);
        Cycle cycle = snapshot(userId);
        clock.setToday(cycle.getCycleStart());

        getCurrent(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cycle.getId()))
                .andExpect(jsonPath("$.label").value("2026-06"))
                .andExpect(jsonPath("$.income").value(2_473_110))
                .andExpect(jsonPath("$.incomeConfirmed").value(false))
                // 통장 첫 등장 순서(항목 입력순) 보존: 국민 → 기타통장 → 생활비통장(LIVING).
                .andExpect(jsonPath("$.checklist.length()").value(3))
                .andExpect(jsonPath("$.checklist[0].accountName").value("국민"))
                .andExpect(jsonPath("$.checklist[0].total").value(700_000))
                .andExpect(jsonPath("$.checklist[0].lines.length()").value(1))
                .andExpect(jsonPath("$.checklist[0].lines[0].name").value("청년도약계좌"))
                .andExpect(jsonPath("$.checklist[0].lines[0].status").value("PENDING"))
                .andExpect(jsonPath("$.checklist[1].accountName").value("기타통장"))
                .andExpect(jsonPath("$.checklist[1].total").value(1_417_003))
                .andExpect(jsonPath("$.checklist[1].lines.length()").value(5))
                .andExpect(jsonPath("$.checklist[2].accountName").value("생활비통장"))
                .andExpect(jsonPath("$.checklist[2].total").value(356_107))
                .andExpect(jsonPath("$.checklist[2].lines[0].name").value("LIVING"))
                // 전체 7라인, 처리된 라인 0.
                .andExpect(jsonPath("$.progress.total").value(7))
                .andExpect(jsonPath("$.progress.done").value(0));
    }

    @Test
    void 라인을_완료로_전이하면_상태와_진행도가_바뀐다() throws Exception {
        long userId = newUser("done");
        seedNotion(userId);
        Cycle cycle = snapshot(userId);
        clock.setToday(cycle.getCycleStart());
        long etf = lineId(cycle.getId(), "ETF");

        patchLine(userId, etf, "DONE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(etf))
                .andExpect(jsonPath("$.name").value("ETF"))
                .andExpect(jsonPath("$.plannedAmount").value(800_000))
                .andExpect(jsonPath("$.status").value("DONE"));

        // checked_at이 주입 시각으로 기록된다.
        PlanLine line = planLineRepository.findById(etf).orElseThrow();
        assertThat(line.getStatus()).isEqualTo(PlanLineStatus.DONE);
        assertThat(line.getCheckedAt()).isEqualTo(clock.instant());

        // 진행도 1/7.
        getCurrent(userId).andExpect(jsonPath("$.progress.done").value(1));
    }

    @Test
    void 건너뜀도_처리된_라인으로_집계된다() throws Exception {
        long userId = newUser("skip");
        seedNotion(userId);
        Cycle cycle = snapshot(userId);
        clock.setToday(cycle.getCycleStart());
        long netflix = lineId(cycle.getId(), "넷플릭스");

        patchLine(userId, netflix, "SKIPPED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));

        // 건너뜀도 PENDING이 아니므로 done에 포함된다(처리된 라인 = done).
        getCurrent(userId).andExpect(jsonPath("$.progress.done").value(1));
        assertThat(planLineRepository.findById(netflix).orElseThrow().getCheckedAt())
                .isEqualTo(clock.instant());
    }

    @Test
    void 완료를_다시_대기로_되돌리면_처리_시각이_비워진다() throws Exception {
        long userId = newUser("undo");
        seedNotion(userId);
        Cycle cycle = snapshot(userId);
        clock.setToday(cycle.getCycleStart());
        long etf = lineId(cycle.getId(), "ETF");

        patchLine(userId, etf, "DONE").andExpect(status().isOk());
        patchLine(userId, etf, "PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        PlanLine line = planLineRepository.findById(etf).orElseThrow();
        assertThat(line.getStatus()).isEqualTo(PlanLineStatus.PENDING);
        assertThat(line.getCheckedAt()).isNull();
        getCurrent(userId).andExpect(jsonPath("$.progress.done").value(0));
    }

    @Test
    void 잘못된_상태값은_400이고_라인은_그대로다() throws Exception {
        long userId = newUser("badstatus");
        seedNotion(userId);
        Cycle cycle = snapshot(userId);
        clock.setToday(cycle.getCycleStart());
        long etf = lineId(cycle.getId(), "ETF");

        patchLine(userId, etf, "ARCHIVED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(planLineRepository.findById(etf).orElseThrow().getStatus()).isEqualTo(PlanLineStatus.PENDING);
    }

    @Test
    void 타인의_라인은_상태를_바꿀_수_없고_404다() throws Exception {
        long ownerId = newUser("owner");
        seedNotion(ownerId);
        Cycle cycle = snapshot(ownerId);
        clock.setToday(cycle.getCycleStart());
        long etf = lineId(cycle.getId(), "ETF");
        long otherId = newUser("intruder");

        patchLine(otherId, etf, "DONE")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(planLineRepository.findById(etf).orElseThrow().getStatus()).isEqualTo(PlanLineStatus.PENDING);
    }

    @Test
    void 존재하지_않는_라인은_404다() throws Exception {
        long userId = newUser("missing");
        seedNotion(userId);
        snapshot(userId);

        patchLine(userId, 999_999L, "DONE")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 지난_사이클의_라인은_잠겨_상태를_바꿀_수_없다() throws Exception {
        long userId = newUser("past");
        seedNotion(userId);
        Cycle cycle = snapshot(userId);
        long etf = lineId(cycle.getId(), "ETF");

        // 오늘을 cycle_end 다음 날로 옮기면 과거 사이클 — 불변(구현규칙 2장).
        clock.setToday(cycle.getCycleEnd().plusDays(1));

        patchLine(userId, etf, "DONE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CYCLE_LOCKED"));

        assertThat(planLineRepository.findById(etf).orElseThrow().getStatus()).isEqualTo(PlanLineStatus.PENDING);
    }

    @Test
    void 현재_사이클이_없으면_404다() throws Exception {
        long userId = newUser("nocycle");
        seedNotion(userId);
        Cycle cycle = snapshot(userId);

        // 오늘이 사이클 경계 밖이면 현재 사이클이 없다 → 스냅샷 생성 동선으로.
        clock.setToday(cycle.getCycleEnd().plusDays(5));

        getCurrent(userId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
