package com.jinhyoung.salary.cycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.cycle.infra.PlanLine;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 지급일 사이클 스냅샷 트리거(CYCLE-03 후속) 통합 테스트. 실 PostgreSQL(Testcontainers) + 날짜를 바꿔 끼울 수 있는
 * KST Clock(규칙 3 주입)으로, 오늘이 실제 지급일인 사용자에게만 사이클 스냅샷이 박히는지(비지급일·온보딩 전 제외),
 * 같은 날 재실행이 멱등인지, 월 경계 조정으로 밀린 실지급일에 올바른 명목 월(시작 월 라벨) 사이클이 생기는지를
 * 결정론적으로 검증한다. 스냅샷 내용(LIVING 356,107 등)은 {@code CycleSnapshotServiceIntegrationTest}가 덮으므로,
 * 여기서는 "트리거가 올바른 사용자·달을 골라 호출하는가"에 집중한다.
 */
@SpringBootTest
@Testcontainers
@Import(PaydaySnapshotServiceIntegrationTest.MutableClockConfig.class)
class PaydaySnapshotServiceIntegrationTest {

    /** 테스트마다 기준일을 바꿔 끼우기 위한 가변 KST Clock(now() 직접 호출 대신 주입 — 규칙 3). */
    static final class MutableClock extends Clock {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");
        private volatile Instant instant =
                LocalDate.of(2026, 1, 1).atStartOfDay(KST).toInstant();

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
    PaydaySnapshotService paydaySnapshotService;

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
    MutableClock clock;

    @BeforeEach
    void setUp() {
        planLineRepository.deleteAll();
        cycleRepository.deleteAll();
        budgetItemRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** 온보딩 완료 사용자 + 생활비 통장 1개 지정(LIVING 라인 생성용). 항목은 없어 LIVING=실수령액 전액. */
    private long saveOnboardedUser(String key, int payday, PaydayAdjustment adjustment) {
        long userId = userRepository
                .save(User.createFromOAuth("KAKAO", key, key + "@x.com", key))
                .getId();
        long living = accountRepository
                .save(Account.create(userId, "생활비통장", null, null, 0))
                .getId();
        User user = userRepository.findById(userId).orElseThrow();
        user.updateSettings(2_473_110L, (short) payday, adjustment, living);
        userRepository.save(user);
        return userId;
    }

    @Test
    void 오늘이_실지급일인_사용자에게만_사이클_스냅샷을_만든다() {
        // 2026-01-26(월) = today. A는 26일 지급(평일 그대로) → 대상, B는 15일 지급 → 비대상.
        long alice = saveOnboardedUser("alice", 26, PaydayAdjustment.NONE);
        saveOnboardedUser("bob", 15, PaydayAdjustment.NONE);
        clock.setToday(LocalDate.of(2026, 1, 26));

        int snapshotted = paydaySnapshotService.createTodaysSnapshots();

        assertThat(snapshotted).isEqualTo(1);
        assertThat(cycleRepository.findAll())
                .extracting(Cycle::getUserId, Cycle::getCycleStart, Cycle::getLabel)
                .containsExactly(tuple(alice, LocalDate.of(2026, 1, 26), "2026-01"));
    }

    @Test
    void 비지급일에는_아무_스냅샷도_만들지_않는다() {
        saveOnboardedUser("alice", 26, PaydayAdjustment.NONE);
        saveOnboardedUser("bob", 15, PaydayAdjustment.NONE);
        clock.setToday(LocalDate.of(2026, 1, 20)); // 두 사람 모두 지급일이 아님

        int snapshotted = paydaySnapshotService.createTodaysSnapshots();

        assertThat(snapshotted).isZero();
        assertThat(cycleRepository.findAll()).isEmpty();
    }

    @Test
    void 같은_날_재실행해도_멱등이라_사이클이_중복_생성되지_않는다() {
        long alice = saveOnboardedUser("alice", 26, PaydayAdjustment.NONE);
        clock.setToday(LocalDate.of(2026, 1, 26));

        paydaySnapshotService.createTodaysSnapshots();
        int second = paydaySnapshotService.createTodaysSnapshots();

        // 두 번째 호출도 대상으로 세지만(지급일 당일) 멱등 게이트가 중복 적재를 막아 사이클은 1건뿐.
        assertThat(second).isEqualTo(1);
        assertThat(cycleRepository.findAll())
                .extracting(Cycle::getUserId, Cycle::getCycleStart)
                .containsExactly(tuple(alice, LocalDate.of(2026, 1, 26)));
        // LIVING 라인도 한 번만 박힌다(항목 없음 → LIVING 1건).
        long aliceCycleId = cycleRepository.findAll().get(0).getId();
        List<PlanLine> lines = planLineRepository.findByCycleIdOrderByIdAsc(aliceCycleId);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getLineType().name()).isEqualTo("LIVING");
    }

    @Test
    void 온보딩_전_플레이스홀더_사용자는_지급일이_겹쳐도_제외한다() {
        // createFromOAuth 플레이스홀더: base_income=0, payday=1(NONE). 2026-01-01이 명목 지급일이지만 온보딩 전이라 제외.
        userRepository.save(User.createFromOAuth("KAKAO", "newbie", "newbie@x.com", "newbie"));
        clock.setToday(LocalDate.of(2026, 1, 1));

        int snapshotted = paydaySnapshotService.createTodaysSnapshots();

        assertThat(snapshotted).isZero();
        assertThat(cycleRepository.findAll()).isEmpty();
    }

    @Test
    void 월말_NEXT_조정으로_밀린_실지급일에는_그_명목_월_사이클을_만든다() {
        // 월급일 31일 + NEXT. 2026-01-31(토) → 2/1(일) → 2/2(월)로 이동. 명목 1월 지급일이 2월 2일에 떨어진다.
        // 트리거가 today(2/2)가 속한 2월이 아니라 명목 1월을 시작 월로 골라야 라벨이 "2026-01"이 된다.
        long carol = saveOnboardedUser("carol", 31, PaydayAdjustment.NEXT_BUSINESS_DAY);
        clock.setToday(LocalDate.of(2026, 2, 2));

        int snapshotted = paydaySnapshotService.createTodaysSnapshots();

        assertThat(snapshotted).isEqualTo(1);
        assertThat(cycleRepository.findAll())
                .extracting(Cycle::getUserId, Cycle::getCycleStart, Cycle::getLabel)
                .containsExactly(tuple(carol, LocalDate.of(2026, 2, 2), "2026-01"));
    }
}
