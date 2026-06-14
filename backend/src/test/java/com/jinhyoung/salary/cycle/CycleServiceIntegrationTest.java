package com.jinhyoung.salary.cycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jinhyoung.salary.cycle.domain.CycleDefinition;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.cycle.infra.Holiday;
import com.jinhyoung.salary.cycle.infra.HolidayRepository;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 사이클 경계 산출 적용(CYCLE-02) 통합 테스트. 실 PostgreSQL(Testcontainers)에 공휴일을 적재한 뒤 {@link
 * CycleService}가 캐시된 공휴일을 주입해 실지급일~다음 지급일 전날 경계를 산출하는지, 캐시가 비면 주말 회피만
 * 하는 폴백이 되는지, 그리고 {@link Cycle} 엔티티의 {@code unique(user_id, cycle_start)} 멱등 제약이
 * 동작하는지를 검증한다. 순수 경계 계산 자체는 {@code CycleResolverTest}가 별도로 덮는다.
 */
@SpringBootTest
@Testcontainers
class CycleServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CycleService cycleService;

    @Autowired
    CycleRepository cycleRepository;

    @Autowired
    HolidayRepository holidayRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void clear() {
        cycleRepository.deleteAll();
        userRepository.deleteAll();
        holidayRepository.deleteAll();
    }

    @Test
    void 공휴일이_적재되면_시작일이_공휴일을_피해_이동한다() {
        holidayRepository.save(Holiday.of(LocalDate.of(2026, 1, 1), "신정", 2026));

        CycleDefinition cycle = cycleService.resolveCycle(YearMonth.of(2026, 1), 1, PaydayAdjustment.NEXT_BUSINESS_DAY);

        assertThat(cycle.cycleStart()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(cycle.cycleEnd()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(cycle.label()).isEqualTo("2026-01");
    }

    @Test
    void 캐시가_비면_주말_회피만_수행한다() {
        // 공휴일 캐시 없음(차년도 미제공·API 장애 폴백) — 1/1(목)은 평일이라 그대로, 다음 지급일 2/1(일)만 주말 회피.
        CycleDefinition cycle = cycleService.resolveCycle(YearMonth.of(2026, 1), 1, PaydayAdjustment.NEXT_BUSINESS_DAY);

        assertThat(cycle.cycleStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(cycle.cycleEnd()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void 연속한_두_사이클의_경계가_맞닿는다() {
        CycleDefinition jan = cycleService.resolveCycle(YearMonth.of(2026, 1), 25, PaydayAdjustment.NONE);
        CycleDefinition feb = cycleService.resolveCycle(YearMonth.of(2026, 2), 25, PaydayAdjustment.NONE);

        assertThat(jan.cycleEnd().plusDays(1)).isEqualTo(feb.cycleStart());
    }

    @Test
    void 월말_월급일은_말일로_clamp한_뒤_조정한다() {
        // 월급일 31일의 2/28(토) clamp → PREV → 2/27(금), 다음 달 3/31(화)은 평일 → 경계 끝 3/30.
        CycleDefinition cycle =
                cycleService.resolveCycle(YearMonth.of(2026, 2), 31, PaydayAdjustment.PREV_BUSINESS_DAY);

        assertThat(cycle.cycleStart()).isEqualTo(LocalDate.of(2026, 2, 27));
        assertThat(cycle.cycleEnd()).isEqualTo(LocalDate.of(2026, 3, 30));
        assertThat(cycle.label()).isEqualTo("2026-02");
    }

    @Test
    void 사이클은_저장되고_동일_경계_재저장은_멱등제약에_막힌다() {
        User user = userRepository.save(User.createFromOAuth("kakao", "cycle-it-1", null, "tester"));
        CycleDefinition def = cycleService.resolveCycle(YearMonth.of(2026, 1), 25, PaydayAdjustment.NONE);

        cycleRepository.saveAndFlush(Cycle.create(user.getId(), def, 2_473_110L));

        assertThat(cycleRepository.existsByUserIdAndCycleStart(user.getId(), def.cycleStart()))
                .isTrue();
        assertThat(cycleRepository.findByUserIdAndCycleStart(user.getId(), def.cycleStart()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getCycleEnd()).isEqualTo(def.cycleEnd());
                    assertThat(saved.getLabel()).isEqualTo("2026-01");
                    assertThat(saved.isIncomeConfirmed()).isFalse();
                });

        // 같은 (user_id, cycle_start)로 다시 만들면 unique 제약(uq_cycles_user_start)에 막힌다 — 스냅샷 멱등의 근거.
        Cycle duplicate = Cycle.create(user.getId(), def, 9_999_999L);
        assertThatThrownBy(() -> cycleRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
