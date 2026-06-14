package com.jinhyoung.salary.budgetitem;

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
import com.jinhyoung.salary.budgetitem.infra.ItemStatus;
import com.jinhyoung.salary.cycle.CycleSnapshotService;
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
 * 항목 수정 적용 시점(ITEM-07) 통합 테스트. 실 PostgreSQL(Testcontainers) + MockMvc + 실 JWT.
 *
 * <p>핵심 의미론 검증:
 *
 * <ul>
 *   <li>기본 {@code PATCH /budget-items/{id}}는 budget_items 원본만 바꾼다 — 현재 사이클 plan_lines는 불변
 *       스냅샷이라 그대로(수정값은 다음 사이클 생성 시 반영, "다음 사이클부터 적용").
 *   <li>{@code ?applyToCurrentCycle=true}일 때만 현재 사이클을 재생성한다(구현규칙 4장): PENDING·SKIPPED는
 *       새 값으로 다시 박고, 완료된(DONE) 라인은 보존하며 그 항목은 재생성에서 제외(이중 이체 방지), LIVING 재계산.
 * </ul>
 *
 * <p>기준 데이터는 노션 실데이터(income 2,473,110): ITEM 6건(합 2,117,003) + LIVING 356,107. "오늘"은 사이클
 * 경계에 끼우는 가변 KST {@code Clock}(규칙 3 주입)으로 결정론 검증한다(payday 25·NONE → 6월 사이클 6/25~7/24).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(BudgetItemUpdateIntegrationTest.MutableClockConfig.class)
class BudgetItemUpdateIntegrationTest {

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

    private Cycle snapshotAndEnter(long userId) {
        Cycle cycle = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));
        clock.setToday(cycle.getCycleStart());
        return cycle;
    }

    private String token(long userId) {
        return "Bearer " + jwtProvider.issue(userId);
    }

    private long itemId(long userId, String name) {
        return budgetItemRepository.findByUserIdAndStatusOrderBySortOrderAsc(userId, ItemStatus.ACTIVE).stream()
                .filter(item -> item.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private long accountIdOf(long userId, String name) {
        return budgetItemRepository.findByUserIdAndStatusOrderBySortOrderAsc(userId, ItemStatus.ACTIVE).stream()
                .map(BudgetItem::getAccountId)
                .filter(id ->
                        accountRepository.findById(id).orElseThrow().getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private PlanLine planLine(long cycleId, String nameSnapshot) {
        return planLineRepository.findByCycleIdOrderByIdAsc(cycleId).stream()
                .filter(line -> line.getNameSnapshot().equals(nameSnapshot))
                .findFirst()
                .orElse(null);
    }

    private long lineCount(long cycleId) {
        return planLineRepository.findByCycleIdOrderByIdAsc(cycleId).size();
    }

    private String itemBody(Category category, String name, long amount, long accountId) {
        return "{\"category\":\"" + category + "\",\"name\":\"" + name + "\",\"amount\":" + amount + ",\"accountId\":"
                + accountId + ",\"startDate\":\"2026-06-01\"}";
    }

    private ResultActions patchItem(long userId, long itemId, boolean applyToCurrentCycle, String body)
            throws Exception {
        String url = "/api/v1/budget-items/" + itemId + (applyToCurrentCycle ? "?applyToCurrentCycle=true" : "");
        return mockMvc.perform(patch(url)
                .header(HttpHeaders.AUTHORIZATION, token(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void markLine(long userId, long cycleId, String nameSnapshot, String statusValue) throws Exception {
        long lineId = planLine(cycleId, nameSnapshot).getId();
        mockMvc.perform(patch("/api/v1/plan-lines/" + lineId)
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + statusValue + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 기본_수정은_항목만_바꾸고_현재_사이클_라인은_불변이다() throws Exception {
        long userId = newUser("default");
        seedNotion(userId);
        Cycle cycle = snapshotAndEnter(userId);
        long etf = itemId(userId, "ETF");
        long etc = accountIdOf(userId, "기타통장");

        // applyToCurrentCycle 없이 ETF 800,000 → 900,000 수정.
        patchItem(userId, etf, false, itemBody(Category.INVESTMENT, "ETF", 900_000, etc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(900_000));

        // 원본은 바뀌고(다음 사이클에 반영) 현재 사이클 plan_line은 스냅샷이라 그대로다.
        assertThat(budgetItemRepository.findById(etf).orElseThrow().getAmount()).isEqualTo(900_000L);
        assertThat(planLine(cycle.getId(), "ETF").getPlannedAmount()).isEqualTo(800_000L);
        assertThat(planLine(cycle.getId(), "LIVING").getPlannedAmount()).isEqualTo(356_107L);
        assertThat(lineCount(cycle.getId())).isEqualTo(7);
    }

    @Test
    void 이번달반영하면_미완료_라인과_LIVING이_재계산된다() throws Exception {
        long userId = newUser("apply");
        seedNotion(userId);
        Cycle cycle = snapshotAndEnter(userId);
        long etf = itemId(userId, "ETF");
        long etc = accountIdOf(userId, "기타통장");

        // ETF 800,000 → 900,000 + 이번 달 반영. 비-LIVING 합 2,217,003 → LIVING = 2,473,110 − 2,217,003 = 256,107.
        patchItem(userId, etf, true, itemBody(Category.INVESTMENT, "ETF", 900_000, etc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(900_000));

        assertThat(planLine(cycle.getId(), "ETF").getPlannedAmount()).isEqualTo(900_000L);
        assertThat(planLine(cycle.getId(), "LIVING").getPlannedAmount()).isEqualTo(256_107L);
        // 라인 수는 그대로 7, 재생성된 라인은 전부 PENDING(미완료였으니).
        assertThat(lineCount(cycle.getId())).isEqualTo(7);
        assertThat(planLineRepository.findByCycleIdOrderByIdAsc(cycle.getId()))
                .allMatch(line -> line.getStatus() == PlanLineStatus.PENDING);
    }

    @Test
    void 완료된_라인은_보존되고_그_항목은_재생성에서_제외된다() throws Exception {
        long userId = newUser("donelock");
        seedNotion(userId);
        Cycle cycle = snapshotAndEnter(userId);
        long etf = itemId(userId, "ETF");
        long etc = accountIdOf(userId, "기타통장");

        // ETF 라인을 먼저 완료(이미 이체) 처리한 뒤, 항목 금액을 바꾸고 이번 달 반영.
        markLine(userId, cycle.getId(), "ETF", "DONE");
        patchItem(userId, etf, true, itemBody(Category.INVESTMENT, "ETF", 900_000, etc))
                .andExpect(status().isOk());

        // DONE 라인은 옛 금액(800,000)·DONE 상태로 보존되고, ETF는 재생성에서 빠져 중복 라인이 생기지 않는다.
        PlanLine etfLine = planLine(cycle.getId(), "ETF");
        assertThat(etfLine.getPlannedAmount()).isEqualTo(800_000L);
        assertThat(etfLine.getStatus()).isEqualTo(PlanLineStatus.DONE);
        assertThat(planLineRepository.findByCycleIdOrderByIdAsc(cycle.getId()).stream()
                        .filter(line -> line.getNameSnapshot().equals("ETF"))
                        .count())
                .isEqualTo(1);
        // ETF가 800,000으로 보존돼 비-LIVING 합이 그대로라 LIVING도 356,107 불변.
        assertThat(planLine(cycle.getId(), "LIVING").getPlannedAmount()).isEqualTo(356_107L);
        assertThat(lineCount(cycle.getId())).isEqualTo(7);
    }

    @Test
    void 건너뛴_라인은_삭제되고_새_값으로_다시_생성된다() throws Exception {
        long userId = newUser("skip");
        seedNotion(userId);
        Cycle cycle = snapshotAndEnter(userId);
        long netflix = itemId(userId, "넷플릭스");
        long etc = accountIdOf(userId, "기타통장");

        // 넷플릭스 라인을 건너뜀 처리 → 항목 금액 변경 + 이번 달 반영(SKIPPED은 DONE이 아니라 삭제·재생성 대상).
        markLine(userId, cycle.getId(), "넷플릭스", "SKIPPED");
        patchItem(userId, netflix, true, itemBody(Category.SUBSCRIPTION, "넷플릭스", 20_000, etc))
                .andExpect(status().isOk());

        // 새 금액(20,000)·PENDING으로 재생성. 비-LIVING 합 2,126,253 → LIVING = 2,473,110 − 2,126,253 = 346,857.
        PlanLine line = planLine(cycle.getId(), "넷플릭스");
        assertThat(line.getPlannedAmount()).isEqualTo(20_000L);
        assertThat(line.getStatus()).isEqualTo(PlanLineStatus.PENDING);
        assertThat(planLine(cycle.getId(), "LIVING").getPlannedAmount()).isEqualTo(346_857L);
        assertThat(lineCount(cycle.getId())).isEqualTo(7);
    }

    @Test
    void 항목을_추가하고_이번달반영하면_현재_사이클에_라인이_생긴다() throws Exception {
        long userId = newUser("add");
        seedNotion(userId);
        Cycle cycle = snapshotAndEnter(userId);
        long etf = itemId(userId, "ETF");
        long savings = accountIdOf(userId, "국민");
        long etc = accountIdOf(userId, "기타통장");

        // 새 항목 추가(POST) — 활성 항목이 7개가 된다.
        mockMvc.perform(post("/api/v1/budget-items")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody(Category.SAVING, "추가적금", 50_000, savings)))
                .andExpect(status().isCreated());

        // 기존 항목을 같은 값으로 PATCH하며 이번 달 반영 → 재생성이 현재 ACTIVE 항목 전부를 반영.
        patchItem(userId, etf, true, itemBody(Category.INVESTMENT, "ETF", 800_000, etc))
                .andExpect(status().isOk());

        // 추가 항목 라인이 현재 사이클에 생기고, LIVING = 2,473,110 − (2,117,003 + 50,000) = 306,107.
        assertThat(planLine(cycle.getId(), "추가적금").getPlannedAmount()).isEqualTo(50_000L);
        assertThat(planLine(cycle.getId(), "LIVING").getPlannedAmount()).isEqualTo(306_107L);
        assertThat(lineCount(cycle.getId())).isEqualTo(8); // ITEM 7 + LIVING 1
    }

    @Test
    void 현재_사이클이_없으면_이번달반영은_무시되고_항목만_바뀐다() throws Exception {
        long userId = newUser("nocycle");
        seedNotion(userId);
        Cycle cycle = cycleSnapshotService.createSnapshot(userId, YearMonth.of(2026, 6));
        long etf = itemId(userId, "ETF");
        long etc = accountIdOf(userId, "기타통장");

        // 오늘이 사이클 경계 밖이면 현재 사이클이 없다 → 재생성은 no-op(에러 없이 항목만 수정).
        clock.setToday(cycle.getCycleEnd().plusDays(5));
        patchItem(userId, etf, true, itemBody(Category.INVESTMENT, "ETF", 900_000, etc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(900_000));

        assertThat(budgetItemRepository.findById(etf).orElseThrow().getAmount()).isEqualTo(900_000L);
        // 경계 밖 사이클의 스냅샷은 건드리지 않는다.
        assertThat(planLine(cycle.getId(), "ETF").getPlannedAmount()).isEqualTo(800_000L);
        assertThat(planLine(cycle.getId(), "LIVING").getPlannedAmount()).isEqualTo(356_107L);
        assertThat(lineCount(cycle.getId())).isEqualTo(7);
    }

    @Test
    void 타인의_항목은_수정할_수_없고_404다() throws Exception {
        long ownerId = newUser("owner");
        seedNotion(ownerId);
        Cycle cycle = snapshotAndEnter(ownerId);
        long etf = itemId(ownerId, "ETF");
        long etc = accountIdOf(ownerId, "기타통장");
        long otherId = newUser("intruder");

        patchItem(otherId, etf, true, itemBody(Category.INVESTMENT, "ETF", 900_000, etc))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 남의 항목·사이클은 한 글자도 바뀌지 않는다.
        assertThat(budgetItemRepository.findById(etf).orElseThrow().getAmount()).isEqualTo(800_000L);
        assertThat(planLine(cycle.getId(), "ETF").getPlannedAmount()).isEqualTo(800_000L);
    }

    @Test
    void 범위를_벗어난_금액은_400이고_아무것도_바뀌지_않는다() throws Exception {
        long userId = newUser("invalid");
        seedNotion(userId);
        Cycle cycle = snapshotAndEnter(userId);
        long etf = itemId(userId, "ETF");
        long etc = accountIdOf(userId, "기타통장");

        // 0원은 구현규칙 5장(1~10억) 위반 → VALIDATION_FAILED. 검증이 먼저라 재생성도 일어나지 않는다.
        patchItem(userId, etf, true, itemBody(Category.INVESTMENT, "ETF", 0, etc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(budgetItemRepository.findById(etf).orElseThrow().getAmount()).isEqualTo(800_000L);
        assertThat(planLine(cycle.getId(), "ETF").getPlannedAmount()).isEqualTo(800_000L);
    }
}
