package com.jinhyoung.salary.cycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhyoung.salary.cycle.infra.Holiday;
import com.jinhyoung.salary.cycle.infra.HolidayRepository;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실지급일 산출 적용(CYCLE-01) 통합 테스트. 실 PostgreSQL(Testcontainers)에 공휴일을 적재한 뒤 {@link
 * PaydayService}가 캐시된 공휴일을 PaydayResolver에 주입해 실지급일을 산출하는지, 캐시가 비면 주말 회피만
 * 수행하는 폴백이 되는지를 결정론적으로 검증한다. 순수 계산 자체는 PaydayResolver 단위·골든 테스트가 별도로 덮는다.
 */
@SpringBootTest
@Testcontainers
class PaydayServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    PaydayService paydayService;

    @Autowired
    HolidayRepository holidayRepository;

    @BeforeEach
    void clearHolidays() {
        holidayRepository.deleteAll();
    }

    @Test
    void 공휴일이_적재되면_지급일이_공휴일을_피해_이동한다() {
        // 2026-01-01(목)은 평일이지만 신정 공휴일 — NEXT 규칙이면 다음 영업일 1/2(금)로 이동.
        holidayRepository.save(Holiday.of(LocalDate.of(2026, 1, 1), "신정", 2026));

        LocalDate resolved =
                paydayService.resolveActualPayday(YearMonth.of(2026, 1), 1, PaydayAdjustment.NEXT_BUSINESS_DAY);

        assertThat(resolved).isEqualTo(LocalDate.of(2026, 1, 2));
    }

    @Test
    void 캐시가_비면_공휴일은_무시하고_평일이면_그대로_둔다() {
        // 같은 1/1(목)이지만 공휴일 캐시가 비어 있으면(차년도 미제공·API 장애 폴백) 평일이므로 이동 없음.
        LocalDate resolved =
                paydayService.resolveActualPayday(YearMonth.of(2026, 1), 1, PaydayAdjustment.NEXT_BUSINESS_DAY);

        assertThat(resolved).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void 캐시가_비어도_주말_회피는_수행한다() {
        // 2026-01-03은 토요일 — 공휴일 캐시 없이도 주말 회피만으로 다음 영업일 1/5(월)로 이동.
        LocalDate resolved =
                paydayService.resolveActualPayday(YearMonth.of(2026, 1), 3, PaydayAdjustment.NEXT_BUSINESS_DAY);

        assertThat(resolved).isEqualTo(LocalDate.of(2026, 1, 5));
    }

    @Test
    void 해당_월에_없는_날짜는_말일로_clamp한_뒤_조정한다() {
        // 월급일 31일의 2026-02는 말일 2/28(토)로 clamp → PREV 규칙이면 직전 영업일 2/27(금).
        LocalDate resolved =
                paydayService.resolveActualPayday(YearMonth.of(2026, 2), 31, PaydayAdjustment.PREV_BUSINESS_DAY);

        assertThat(resolved).isEqualTo(LocalDate.of(2026, 2, 27));
    }
}
