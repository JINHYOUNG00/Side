package com.jinhyoung.salary.report;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.jinhyoung.salary.cycle.infra.PlanLine;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.envelope.infra.Envelope;
import com.jinhyoung.salary.envelope.infra.EnvelopeRepository;
import com.jinhyoung.salary.envelope.infra.EnvelopeTransaction;
import com.jinhyoung.salary.envelope.infra.EnvelopeTransactionRepository;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 추이·요약 리포트(RPT-02) 통합 테스트. 실 PostgreSQL(Testcontainers) + MockMvc + 실 JWT.
 *
 * <p>추이는 사이클·LIVING 계획 라인·체크인을 직접 박아 계획 vs 실제·결측 구분·정렬·범위·소유권을 검증한다.
 * 요약은 폭포(저축률)·보관 항목(만기 누적)·봉투 트랜잭션(집행 합계)을 재사용 집계하는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    BudgetItemRepository budgetItemRepository;

    @Autowired
    CycleRepository cycleRepository;

    @Autowired
    PlanLineRepository planLineRepository;

    @Autowired
    CheckInRepository checkInRepository;

    @Autowired
    EnvelopeRepository envelopeRepository;

    @Autowired
    EnvelopeTransactionRepository envelopeTransactionRepository;

    @BeforeEach
    void clear() {
        checkInRepository.deleteAll();
        planLineRepository.deleteAll();
        envelopeTransactionRepository.deleteAll();
        cycleRepository.deleteAll();
        envelopeRepository.deleteAll();
        budgetItemRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private long newUser(String key) {
        return userRepository
                .save(User.createFromOAuth("KAKAO", key, key + "@x.com", key))
                .getId();
    }

    private long newAccount(long userId, String name) {
        return accountRepository
                .save(Account.create(userId, name, null, null, 0))
                .getId();
    }

    /** 사이클 1건 + 그 사이클의 LIVING(생활비) 계획 라인 1건을 박는다(계획액 = planned). */
    private long newCycleWithLiving(long userId, long accountId, String label, LocalDate start, long planned) {
        long cycleId = cycleRepository
                .save(Cycle.create(userId, new CycleDefinition(start, start.plusDays(29), label), 2_473_110))
                .getId();
        planLineRepository.save(PlanLine.living(cycleId, accountId, "생활비통장", planned));
        return cycleId;
    }

    /** 체크인을 박는다(overspend = toppedUp − livingRemaining). */
    private void checkIn(long cycleId, long livingRemaining, long toppedUp) {
        checkInRepository.save(CheckIn.create(cycleId, livingRemaining, toppedUp, null));
    }

    private String token(long userId) {
        return "Bearer " + jwtProvider.issue(userId);
    }

    private ResultActions getTrend(long userId, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/reports/trend" + query).header(HttpHeaders.AUTHORIZATION, token(userId)));
    }

    private ResultActions getSummary(long userId) throws Exception {
        return mockMvc.perform(get("/api/v1/reports/summary").header(HttpHeaders.AUTHORIZATION, token(userId)));
    }

    private ResultActions getAnnual(long userId, int year) throws Exception {
        return mockMvc.perform(
                get("/api/v1/reports/annual?year=" + year).header(HttpHeaders.AUTHORIZATION, token(userId)));
    }

    /** 지정 실수령액(income)으로 그 해 사이클 1건을 박는다(LIVING 라인 없음 — 연간 저축률은 ITEM 라인으로 산정). */
    private long newCycle(long userId, String label, LocalDate start, long income) {
        return cycleRepository
                .save(Cycle.create(userId, new CycleDefinition(start, start.plusDays(29), label), income))
                .getId();
    }

    /** 사이클에 ITEM 라인 1건을 박는다(category 스냅샷 = 카테고리 이름). 연간 저축률 집계 대상. */
    private void itemLine(long cycleId, long accountId, Category category, long planned) {
        planLineRepository.save(
                PlanLine.item(cycleId, null, accountId, category.name(), category.name(), "통장", planned));
    }

    /** 만기일(endDate) 보유 보관 항목을 박는다 — actual이 있으면 실수령액 기록, null이면 미기록 보관. */
    private void archivedItem(long userId, long accountId, LocalDate endDate, Long actual, int order) {
        BudgetItem item = budgetItemRepository.save(BudgetItem.create(
                userId, accountId, Category.SAVING, "적금", 100_000, LocalDate.of(2020, 1, 1), endDate, null, order));
        if (actual != null) {
            item.recordMaturityActual(actual); // ACTIVE → ARCHIVED + 실수령액
        } else {
            item.markArchived();
        }
        budgetItemRepository.save(item);
    }

    // ── 추이(trend) ───────────────────────────────────────────────────────────

    @Test
    void 추이를_시간순으로_계획_실제_체크인여부와_함께_돌려준다() throws Exception {
        long userId = newUser("trend");
        long account = newAccount(userId, "생활비통장");
        // 3개 사이클, 모두 계획 375,000. 4월=초과(+12,000), 5월=결측(체크인 없음), 6월=잉여(−41,000).
        long apr = newCycleWithLiving(userId, account, "2026-04", LocalDate.of(2026, 4, 25), 375_000);
        newCycleWithLiving(userId, account, "2026-05", LocalDate.of(2026, 5, 25), 375_000);
        long jun = newCycleWithLiving(userId, account, "2026-06", LocalDate.of(2026, 6, 25), 375_000);
        checkIn(apr, 0, 12_000); // overspend +12,000 → actual 387,000
        checkIn(jun, 41_000, 0); // overspend −41,000 → actual 334,000

        getTrend(userId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // 시간순(오래된→최근).
                .andExpect(jsonPath("$[0].label").value("2026-04"))
                .andExpect(jsonPath("$[0].planned").value(375_000))
                .andExpect(jsonPath("$[0].actual").value(387_000))
                .andExpect(jsonPath("$[0].checkedIn").value(true))
                // 결측 사이클: 실제는 측정 불가(null), checkedIn=false.
                .andExpect(jsonPath("$[1].label").value("2026-05"))
                .andExpect(jsonPath("$[1].planned").value(375_000))
                .andExpect(jsonPath("$[1].actual").value(nullValue()))
                .andExpect(jsonPath("$[1].checkedIn").value(false))
                .andExpect(jsonPath("$[2].label").value("2026-06"))
                .andExpect(jsonPath("$[2].actual").value(334_000))
                .andExpect(jsonPath("$[2].checkedIn").value(true));
    }

    @Test
    void months로_최근_사이클_수를_제한한다() throws Exception {
        long userId = newUser("limit");
        long account = newAccount(userId, "생활비통장");
        newCycleWithLiving(userId, account, "2026-04", LocalDate.of(2026, 4, 25), 300_000);
        newCycleWithLiving(userId, account, "2026-05", LocalDate.of(2026, 5, 25), 300_000);
        newCycleWithLiving(userId, account, "2026-06", LocalDate.of(2026, 6, 25), 300_000);

        // 최근 2개만 → 5월, 6월(시간순).
        getTrend(userId, "?months=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].label").value("2026-05"))
                .andExpect(jsonPath("$[1].label").value("2026-06"));
    }

    @Test
    void 사이클이_없으면_빈_목록이다() throws Exception {
        long userId = newUser("empty");

        getTrend(userId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void months가_범위를_벗어나면_400이다() throws Exception {
        long userId = newUser("range");

        getTrend(userId, "?months=0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        getTrend(userId, "?months=37")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 타인의_사이클은_추이에_포함되지_않는다() throws Exception {
        long ownerId = newUser("owner");
        long account = newAccount(ownerId, "생활비통장");
        newCycleWithLiving(ownerId, account, "2026-06", LocalDate.of(2026, 6, 25), 375_000);
        long otherId = newUser("other");

        // 남의 사이클은 보이지 않는다(빈 목록).
        getTrend(otherId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 토큰이_없으면_추이는_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/reports/trend")).andExpect(status().isUnauthorized());
    }

    // ── 요약(summary) ─────────────────────────────────────────────────────────

    @Test
    void 요약은_저축률_만기누적_봉투집행을_집계한다() throws Exception {
        long userId = newUser("summary");
        long account = newAccount(userId, "통장");

        // 저축률: income 2,000,000, 활성 SAVING 500,000 → 25.0%(투자 포함 기본 true).
        User user = userRepository.findById(userId).orElseThrow();
        user.updateSettings(2_000_000, (short) 25, PaydayAdjustment.NONE, null);
        userRepository.save(user);
        budgetItemRepository.save(BudgetItem.create(
                userId, account, Category.SAVING, "적금", 500_000, LocalDate.of(2026, 6, 1), null, null, 0));

        // 만기 누적: 보관 2건 중 1건만 실수령액 기록(3,000,000), 1건 미기록.
        BudgetItem received = budgetItemRepository.save(BudgetItem.create(
                userId, account, Category.SAVING, "만기적금", 100_000, LocalDate.of(2025, 1, 1), null, null, 1));
        received.recordMaturityActual(3_000_000); // ACTIVE → ARCHIVED + 실수령액
        budgetItemRepository.save(received);
        BudgetItem unrecorded = budgetItemRepository.save(BudgetItem.create(
                userId, account, Category.SAVING, "보관적금", 100_000, LocalDate.of(2025, 1, 1), null, null, 2));
        unrecorded.markArchived(); // 실수령액 미기록 보관
        budgetItemRepository.save(unrecorded);

        // 봉투 집행: 본인 봉투 SPEND 50,000 + 30,000 = 80,000. DEPOSIT는 집행 아님(제외).
        long envelopeId = envelopeRepository
                .save(Envelope.create(userId, account, "여행", 1_000_000, LocalDate.of(2026, 12, 1), (short) 12, null))
                .getId();
        envelopeTransactionRepository.save(
                EnvelopeTransaction.spend(envelopeId, 50_000, 50_000, null, null, null, LocalDate.of(2026, 6, 1)));
        envelopeTransactionRepository.save(
                EnvelopeTransaction.spend(envelopeId, 30_000, 30_000, null, null, null, LocalDate.of(2026, 6, 2)));
        envelopeTransactionRepository.save(
                EnvelopeTransaction.deposit(envelopeId, 999_999, null, LocalDate.of(2026, 6, 3)));

        // 타인의 봉투 집행은 합산에서 제외돼야 한다.
        long otherId = newUser("noise");
        long otherAccount = newAccount(otherId, "통장");
        long otherEnvelope = envelopeRepository
                .save(Envelope.create(
                        otherId, otherAccount, "남의여행", 1_000_000, LocalDate.of(2026, 12, 1), (short) 12, null))
                .getId();
        envelopeTransactionRepository.save(
                EnvelopeTransaction.spend(otherEnvelope, 777_777, 777_777, null, null, null, LocalDate.of(2026, 6, 1)));

        getSummary(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingsRate.value").value(25.0))
                .andExpect(jsonPath("$.savingsRate.includesInvestment").value(true))
                .andExpect(jsonPath("$.maturity.archivedCount").value(2))
                .andExpect(jsonPath("$.maturity.recordedCount").value(1))
                .andExpect(jsonPath("$.maturity.totalReceivedAmount").value(3_000_000))
                .andExpect(jsonPath("$.envelopeSpentTotal").value(80_000));
    }

    @Test
    void 빈_사용자_요약은_0과_기본_저축률이다() throws Exception {
        long userId = newUser("blank");

        getSummary(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingsRate.value").value(0.0))
                .andExpect(jsonPath("$.savingsRate.includesInvestment").value(true))
                .andExpect(jsonPath("$.maturity.archivedCount").value(0))
                .andExpect(jsonPath("$.maturity.recordedCount").value(0))
                .andExpect(jsonPath("$.maturity.totalReceivedAmount").value(0))
                .andExpect(jsonPath("$.envelopeSpentTotal").value(0));
    }

    @Test
    void 토큰이_없으면_요약은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary")).andExpect(status().isUnauthorized());
    }

    // ── 연간 결산(annual, RPT-04) ─────────────────────────────────────────────

    @Test
    void 연간_결산은_그해_저축률_만기수령_봉투집행을_집계하고_다른해는_제외한다() throws Exception {
        long userId = newUser("annual");
        long account = newAccount(userId, "통장");

        // 저축률: 2025 사이클 income 2,000,000 + SAVING 500,000 + INVESTMENT 300,000 → 투자 포함 기본 true → 40.0%.
        long cycle2025 = newCycle(userId, "2025-03", LocalDate.of(2025, 3, 25), 2_000_000);
        itemLine(cycle2025, account, Category.SAVING, 500_000);
        itemLine(cycle2025, account, Category.INVESTMENT, 300_000);
        // 다른 해(2024) 사이클·저축은 2025 결산에 섞이지 않는다.
        long cycle2024 = newCycle(userId, "2024-03", LocalDate.of(2024, 3, 25), 9_000_000);
        itemLine(cycle2024, account, Category.SAVING, 9_000_000);

        // 만기 수령: 만기일 2025 보관 2건(1건만 실수령 3,000,000 기록) + 만기일 2024 보관 1건(제외).
        archivedItem(userId, account, LocalDate.of(2025, 3, 1), 3_000_000L, 0);
        archivedItem(userId, account, LocalDate.of(2025, 9, 1), null, 1);
        archivedItem(userId, account, LocalDate.of(2024, 6, 1), 9_999_999L, 2);

        // 봉투 집행: 2025 SPEND 50,000 + 30,000 = 80,000. 2024 SPEND·DEPOSIT은 제외.
        long envelopeId = envelopeRepository
                .save(Envelope.create(userId, account, "여행", 1_000_000, LocalDate.of(2026, 12, 1), (short) 12, null))
                .getId();
        envelopeTransactionRepository.save(
                EnvelopeTransaction.spend(envelopeId, 50_000, 50_000, null, null, null, LocalDate.of(2025, 5, 1)));
        envelopeTransactionRepository.save(
                EnvelopeTransaction.spend(envelopeId, 30_000, 30_000, null, null, null, LocalDate.of(2025, 8, 1)));
        envelopeTransactionRepository.save(
                EnvelopeTransaction.spend(envelopeId, 70_000, 70_000, null, null, null, LocalDate.of(2024, 12, 1)));
        envelopeTransactionRepository.save(
                EnvelopeTransaction.deposit(envelopeId, 999_999, null, LocalDate.of(2025, 6, 3)));

        // 타인의 2025 데이터는 합산에서 제외돼야 한다.
        long otherId = newUser("annual-noise");
        long otherAccount = newAccount(otherId, "통장");
        long otherCycle = newCycle(otherId, "2025-03", LocalDate.of(2025, 3, 25), 5_000_000);
        itemLine(otherCycle, otherAccount, Category.SAVING, 5_000_000);
        archivedItem(otherId, otherAccount, LocalDate.of(2025, 4, 1), 7_777_777L, 0);
        long otherEnvelope = envelopeRepository
                .save(Envelope.create(
                        otherId, otherAccount, "남의여행", 1_000_000, LocalDate.of(2026, 12, 1), (short) 12, null))
                .getId();
        envelopeTransactionRepository.save(
                EnvelopeTransaction.spend(otherEnvelope, 444_444, 444_444, null, null, null, LocalDate.of(2025, 7, 1)));

        getAnnual(userId, 2025)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.savingsRate.value").value(40.0))
                .andExpect(jsonPath("$.savingsRate.includesInvestment").value(true))
                .andExpect(jsonPath("$.maturity.archivedCount").value(2))
                .andExpect(jsonPath("$.maturity.recordedCount").value(1))
                .andExpect(jsonPath("$.maturity.totalReceivedAmount").value(3_000_000))
                .andExpect(jsonPath("$.envelopeSpentTotal").value(80_000));
    }

    @Test
    void 투자_제외_토글이면_연간_저축률에서_투자를_뺀다() throws Exception {
        long userId = newUser("annual-noinv");
        long account = newAccount(userId, "통장");
        User user = userRepository.findById(userId).orElseThrow();
        user.updateInvestmentInclusion(false);
        userRepository.save(user);

        // income 2,000,000 + SAVING 500,000 + INVESTMENT 300,000 → 투자 제외 → 500,000/2,000,000 = 25.0%.
        long cycle = newCycle(userId, "2025-03", LocalDate.of(2025, 3, 25), 2_000_000);
        itemLine(cycle, account, Category.SAVING, 500_000);
        itemLine(cycle, account, Category.INVESTMENT, 300_000);

        getAnnual(userId, 2025)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingsRate.value").value(25.0))
                .andExpect(jsonPath("$.savingsRate.includesInvestment").value(false));
    }

    @Test
    void 데이터_없는_해는_0과_기본_저축률이다() throws Exception {
        long userId = newUser("annual-blank");

        getAnnual(userId, 2025)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.savingsRate.value").value(0.0))
                .andExpect(jsonPath("$.savingsRate.includesInvestment").value(true))
                .andExpect(jsonPath("$.maturity.archivedCount").value(0))
                .andExpect(jsonPath("$.maturity.recordedCount").value(0))
                .andExpect(jsonPath("$.maturity.totalReceivedAmount").value(0))
                .andExpect(jsonPath("$.envelopeSpentTotal").value(0));
    }

    @Test
    void year가_범위를_벗어나면_400이다() throws Exception {
        long userId = newUser("annual-range");

        getAnnual(userId, 1999) // 하한(2000) 미만
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        getAnnual(userId, 9999) // 현재 연도+1 초과
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 토큰이_없으면_연간_결산은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/reports/annual?year=2025")).andExpect(status().isUnauthorized());
    }
}
