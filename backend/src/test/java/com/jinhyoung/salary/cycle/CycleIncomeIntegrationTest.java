package com.jinhyoung.salary.cycle;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실수령액 확인(CYCLE-04) 통합 테스트. 실 PostgreSQL(Testcontainers)에 사이클 스냅샷을 박은 뒤
 * {@code PATCH /cycles/{id}/income}으로 실수령액을 확정하고, income_confirmed 전환과 LIVING 라인 재계산
 * (구현규칙 3장)을 검증한다.
 *
 * <p>기준 데이터는 노션 실데이터(income 2,473,110 → LIVING 356,107). 항목·EMERGENCY 라인 합은
 * 2,117,003으로 고정이라, 확인 실수령액을 바꾸면 LIVING = income − 2,117,003으로만 움직인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CycleIncomeIntegrationTest {

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
    }

    private long newUser(String key) {
        return userRepository
                .save(User.createFromOAuth("KAKAO", key, key + "@x.com", key))
                .getId();
    }

    private void configure(long userId, long baseIncome, Long livingAccountId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.updateSettings(baseIncome, (short) 25, PaydayAdjustment.NONE, livingAccountId);
        userRepository.save(user);
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

    /** 노션 실데이터 6항목(합 2,117,003)을 박고 생활비 통장 id를 반환한다. living=false면 생활비 통장 미지정. */
    private long seedNotion(long userId, boolean withLiving) {
        long savings = newAccount(userId, "국민", 0);
        long etc = newAccount(userId, "기타통장", 1);
        Long living = withLiving ? newAccount(userId, "생활비통장", 2) : null;
        newItem(userId, savings, Category.SAVING, "청년도약계좌", 700_000, 0);
        newItem(userId, etc, Category.INVESTMENT, "ETF", 800_000, 1);
        newItem(userId, etc, Category.FIXED, "월세", 310_600, 2);
        newItem(userId, etc, Category.INSURANCE, "실손보험", 95_653, 3);
        newItem(userId, etc, Category.SUBSCRIPTION, "넷플릭스", 10_750, 4);
        newItem(userId, etc, Category.EMERGENCY, "비상금", 200_000, 5);
        configure(userId, 2_473_110, living);
        return living == null ? -1 : living;
    }

    private long snapshot(long userId) {
        return cycleSnapshotService
                .createSnapshot(userId, YearMonth.of(2026, 6))
                .getId();
    }

    private PlanLine livingLine(long cycleId) {
        return planLineRepository.findByCycleIdOrderByIdAsc(cycleId).stream()
                .filter(line -> line.getLineType().name().equals("LIVING"))
                .findFirst()
                .orElse(null);
    }

    private org.springframework.test.web.servlet.ResultActions patchIncome(long userId, long cycleId, long income)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/cycles/" + cycleId + "/income")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"income\":" + income + "}"));
    }

    @Test
    void 스냅샷_직후_실수령액은_평소금액이고_확인_전이다() {
        long userId = newUser("default");
        seedNotion(userId, true);
        long cycleId = snapshot(userId);

        // CYCLE-04 기본값 = 평소 실수령액(base_income), 확인 플래그는 아직 false.
        Cycle cycle = cycleRepository.findById(cycleId).orElseThrow();
        assertThat(cycle.getIncome()).isEqualTo(2_473_110L);
        assertThat(cycle.isIncomeConfirmed()).isFalse();
    }

    @Test
    void 실수령액을_확인하면_확인_플래그가_켜지고_LIVING이_재계산된다() throws Exception {
        long userId = newUser("confirm");
        seedNotion(userId, true);
        long cycleId = snapshot(userId);

        // 평소보다 큰 실수령액(성과급 등)으로 확정 → LIVING = 2,500,000 − 2,117,003 = 382,997.
        patchIncome(userId, cycleId, 2_500_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(2_500_000))
                .andExpect(jsonPath("$.incomeConfirmed").value(true))
                .andExpect(jsonPath("$.label").value("2026-06"));

        Cycle cycle = cycleRepository.findById(cycleId).orElseThrow();
        assertThat(cycle.getIncome()).isEqualTo(2_500_000L);
        assertThat(cycle.isIncomeConfirmed()).isTrue();
        assertThat(livingLine(cycleId).getPlannedAmount()).isEqualTo(382_997L);
        // 항목·EMERGENCY 라인은 스냅샷 고정값 그대로(실수령액 변동은 LIVING만 흡수).
        List<PlanLine> nonLiving = planLineRepository.findByCycleIdOrderByIdAsc(cycleId).stream()
                .filter(line -> !line.getLineType().name().equals("LIVING"))
                .toList();
        assertThat(nonLiving).hasSize(6);
        assertThat(nonLiving.stream().mapToLong(PlanLine::getPlannedAmount).sum())
                .isEqualTo(2_117_003L);
    }

    @Test
    void 실수령액을_낮춰_생활비가_0이하가_되면_LIVING_라인을_제거한다() throws Exception {
        long userId = newUser("shortfall");
        seedNotion(userId, true);
        long cycleId = snapshot(userId);

        // 2,000,000 − 2,117,003 = -117,003 ≤ 0 → 이체할 생활비 없음 → LIVING 라인 제거(FLOW-03 의미론).
        patchIncome(userId, cycleId, 2_000_000).andExpect(status().isOk());

        assertThat(livingLine(cycleId)).isNull();
        // ITEM 6건만 남고 income·확인 플래그는 갱신된다.
        assertThat(planLineRepository.findByCycleIdOrderByIdAsc(cycleId)).hasSize(6);
        Cycle cycle = cycleRepository.findById(cycleId).orElseThrow();
        assertThat(cycle.getIncome()).isEqualTo(2_000_000L);
        assertThat(cycle.isIncomeConfirmed()).isTrue();
    }

    @Test
    void 생활비_통장_미지정이면_LIVING_없이_실수령액만_확인된다() throws Exception {
        long userId = newUser("noliving");
        seedNotion(userId, false);
        long cycleId = snapshot(userId);

        patchIncome(userId, cycleId, 2_600_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(2_600_000))
                .andExpect(jsonPath("$.incomeConfirmed").value(true));

        // LIVING 라인이 애초에 없으니 재계산 대상 없음 — ITEM 6건 그대로(없던 라인을 새로 만들지 않는다).
        assertThat(livingLine(cycleId)).isNull();
        assertThat(planLineRepository.findByCycleIdOrderByIdAsc(cycleId)).hasSize(6);
    }

    @Test
    void 타인의_사이클은_확인할_수_없다() throws Exception {
        long ownerId = newUser("owner");
        seedNotion(ownerId, true);
        long cycleId = snapshot(ownerId);
        long otherId = newUser("other");

        patchIncome(otherId, cycleId, 2_500_000)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 남의 사이클은 한 글자도 바뀌지 않는다.
        Cycle cycle = cycleRepository.findById(cycleId).orElseThrow();
        assertThat(cycle.getIncome()).isEqualTo(2_473_110L);
        assertThat(cycle.isIncomeConfirmed()).isFalse();
    }

    @Test
    void 범위를_벗어난_실수령액은_400이다() throws Exception {
        long userId = newUser("invalid");
        seedNotion(userId, true);
        long cycleId = snapshot(userId);

        // 0원은 구현규칙 5장(1~10억) 위반 → VALIDATION_FAILED, 사이클 미변경.
        patchIncome(userId, cycleId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(cycleRepository.findById(cycleId).orElseThrow().isIncomeConfirmed())
                .isFalse();
    }
}
