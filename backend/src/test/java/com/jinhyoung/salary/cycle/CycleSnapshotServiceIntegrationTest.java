package com.jinhyoung.salary.cycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 사이클 스냅샷 영속화(CYCLE-03) 통합 테스트. 실 PostgreSQL(Testcontainers)에 사용자·통장·활성 항목을 박고
 * {@link CycleSnapshotService}가 cycles·plan_lines를 멱등 적재하는지 검증한다.
 *
 * <p>첫 케이스는 노션 실데이터(income 2,473,110)를 그대로 써서, owner의 폭포 골든과 같은 LIVING
 * planned_amount 356,107이 plan_lines에 그대로 박히는지 교차 확인한다 — 골든 파일은 건드리지 않는다.
 */
@SpringBootTest
@Testcontainers
class CycleSnapshotServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CycleSnapshotService cycleSnapshotService;

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

    private void configure(
            long userId, long baseIncome, int payday, PaydayAdjustment adjustment, Long livingAccountId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.updateSettings(baseIncome, (short) payday, adjustment, livingAccountId);
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

    /** 노션 실데이터 6항목을 sort_order 순으로 박는다. 반환은 (저축통장, 기타통장, 생활비통장) id. */
    private long[] seedNotionItems(long userId) {
        long savings = newAccount(userId, "국민", 0);
        long etc = newAccount(userId, "기타통장", 1);
        long living = newAccount(userId, "생활비통장", 2);
        newItem(userId, savings, Category.SAVING, "청년도약계좌", 700_000, 0);
        newItem(userId, etc, Category.INVESTMENT, "ETF", 800_000, 1);
        newItem(userId, etc, Category.FIXED, "월세", 310_600, 2);
        newItem(userId, etc, Category.INSURANCE, "실손보험", 95_653, 3);
        newItem(userId, etc, Category.SUBSCRIPTION, "넷플릭스", 10_750, 4);
        newItem(userId, etc, Category.EMERGENCY, "비상금", 200_000, 5);
        return new long[] {savings, etc, living};
    }

    @Test
    void 노션_실데이터로_사이클과_plan_lines가_적재되고_LIVING은_356107이다() {
        long userId = newUser("notion");
        long[] accounts = seedNotionItems(userId);
        long living = accounts[2];
        configure(userId, 2_473_110, 25, PaydayAdjustment.NONE, living);

        Cycle cycle = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));

        // 사이클 헤더 — payday 25·NONE → cycleStart 2026-06-25, income=평소 실수령액, 확인 전.
        assertThat(cycle.getCycleStart()).isEqualTo(LocalDate.of(2026, 6, 25));
        assertThat(cycle.getLabel()).isEqualTo("2026-06");
        assertThat(cycle.getIncome()).isEqualTo(2_473_110L);
        assertThat(cycle.isIncomeConfirmed()).isFalse();

        List<PlanLine> lines = planLineRepository.findByCycleIdOrderByIdAsc(cycle.getId());
        // ITEM 6건(EMERGENCY 포함) + LIVING 1건.
        assertThat(lines).hasSize(7);
        assertThat(lines).allSatisfy(line -> assertThat(line.getStatus()).isEqualTo(PlanLineStatus.PENDING));

        // ITEM 라인 — 입력 순서(sort_order) 보존, 이름·카테고리·통장 별칭 스냅샷이 채워진다.
        PlanLine saving = lines.get(0);
        assertThat(saving.getNameSnapshot()).isEqualTo("청년도약계좌");
        assertThat(saving.getCategorySnapshot()).isEqualTo("SAVING");
        assertThat(saving.getAccountNameSnapshot()).isEqualTo("국민");
        assertThat(saving.getPlannedAmount()).isEqualTo(700_000L);
        assertThat(saving.getBudgetItemId()).isNotNull();

        // EMERGENCY 항목도 ITEM 라인이며 category_snapshot으로 구분된다.
        PlanLine emergency = lines.get(5);
        assertThat(emergency.getCategorySnapshot()).isEqualTo("EMERGENCY");
        assertThat(emergency.getPlannedAmount()).isEqualTo(200_000L);

        // LIVING 라인 — 골든과 동일한 356,107, 통장은 생활비 통장, name/category는 머신 토큰 "LIVING"(규칙 7).
        PlanLine livingLine = lines.get(6);
        assertThat(livingLine.getLineType().name()).isEqualTo("LIVING");
        assertThat(livingLine.getPlannedAmount()).isEqualTo(356_107L);
        assertThat(livingLine.getAccountId()).isEqualTo(living);
        assertThat(livingLine.getAccountNameSnapshot()).isEqualTo("생활비통장");
        assertThat(livingLine.getNameSnapshot()).isEqualTo("LIVING");
        assertThat(livingLine.getCategorySnapshot()).isEqualTo("LIVING");
        assertThat(livingLine.getBudgetItemId()).isNull();
    }

    @Test
    void 동일_사이클_재생성은_멱등이라_건수와_값이_불변이다() {
        long userId = newUser("idem");
        long living = seedNotionItems(userId)[2];
        configure(userId, 2_473_110, 25, PaydayAdjustment.NONE, living);

        Cycle first = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));
        Cycle second = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));

        // 같은 (user_id, cycle_start) → 새 사이클을 만들지 않고 기존 헤더를 그대로 돌려준다.
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(cycleRepository.findAll()).hasSize(1);
        // plan_lines도 7건 그대로(중복 적재 없음).
        assertThat(planLineRepository.findByCycleIdOrderByIdAsc(first.getId())).hasSize(7);
        assertThat(planLineRepository.findAll()).hasSize(7);
    }

    @Test
    void 생활비_통장_미지정이면_LIVING_라인을_만들지_않는다() {
        long userId = newUser("noliving");
        seedNotionItems(userId);
        configure(userId, 2_473_110, 25, PaydayAdjustment.NONE, null);

        Cycle cycle = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));

        List<PlanLine> lines = planLineRepository.findByCycleIdOrderByIdAsc(cycle.getId());
        // ITEM 6건만 — LIVING 미생성(폭포 표시만, 구현규칙 3장).
        assertThat(lines).hasSize(6);
        assertThat(lines)
                .noneSatisfy(line -> assertThat(line.getLineType().name()).isEqualTo("LIVING"));
    }

    @Test
    void 과배분이면_LIVING_라인을_만들지_않는다() {
        long userId = newUser("over");
        long account = newAccount(userId, "통장", 0);
        long living = newAccount(userId, "생활비통장", 1);
        newItem(userId, account, Category.FIXED, "월세", 900_000, 0);
        newItem(userId, account, Category.EMERGENCY, "비상금", 200_000, 1);
        // remaining 100,000인데 비상금 200,000을 더하면 생활비 -100,000(과배분) → LIVING 미생성.
        configure(userId, 1_000_000, 25, PaydayAdjustment.NONE, living);

        Cycle cycle = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));

        List<PlanLine> lines = planLineRepository.findByCycleIdOrderByIdAsc(cycle.getId());
        assertThat(lines).hasSize(2);
        assertThat(lines)
                .noneSatisfy(line -> assertThat(line.getLineType().name()).isEqualTo("LIVING"));
    }

    @Test
    void 활성_항목이_없으면_사이클만_생기고_plan_lines는_LIVING만_또는_없다() {
        long userId = newUser("empty");
        long living = newAccount(userId, "생활비통장", 0);
        configure(userId, 3_000_000, 25, PaydayAdjustment.NONE, living);

        Cycle cycle = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));

        List<PlanLine> lines = planLineRepository.findByCycleIdOrderByIdAsc(cycle.getId());
        // 항목이 없으니 생활비 = income 전액 → LIVING 1건만.
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getLineType().name()).isEqualTo("LIVING");
        assertThat(lines.get(0).getPlannedAmount()).isEqualTo(3_000_000L);
    }
}
