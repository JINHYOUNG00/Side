package com.jinhyoung.salary.cycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * PaydayResolver 단위 테스트(CYCLE-01) — 경계·검증·조정 규칙을 골든과 별개로 직접 확인한다.
 * 골든(PaydayGoldenTest)은 달력 시나리오를, 여기서는 입력 검증과 알고리즘 불변식을 다룬다.
 */
class PaydayResolverTest {

    private static final Set<LocalDate> NO_HOLIDAYS = Set.of();

    @Test
    void clampsToLastDayWhenMonthIsShorter() {
        // 2026-02는 28일까지 — payday 31은 말일로 clamp(NONE이라 이동 없음).
        assertThat(PaydayResolver.resolve(YearMonth.of(2026, 2), 31, PaydayAdjustment.NONE, NO_HOLIDAYS))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void leapFebruaryClampsTo29() {
        // 2028은 윤년 — 2월 말일은 29일.
        assertThat(PaydayResolver.resolve(YearMonth.of(2028, 2), 31, PaydayAdjustment.NONE, NO_HOLIDAYS))
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void noneKeepsNominalDayEvenOnWeekend() {
        // 2026-05-23은 토요일 — NONE은 그대로 둔다.
        assertThat(PaydayResolver.resolve(YearMonth.of(2026, 5), 23, PaydayAdjustment.NONE, NO_HOLIDAYS))
                .isEqualTo(LocalDate.of(2026, 5, 23));
    }

    @Test
    void prevMovesBackPastWeekend() {
        // 토(5/23) → PREV → 금(5/22).
        assertThat(PaydayResolver.resolve(YearMonth.of(2026, 5), 23, PaydayAdjustment.PREV_BUSINESS_DAY, NO_HOLIDAYS))
                .isEqualTo(LocalDate.of(2026, 5, 22));
    }

    @Test
    void nextMovesForwardPastWeekend() {
        // 토(5/23) → NEXT → 일 건너뛴 월(5/25).
        assertThat(PaydayResolver.resolve(YearMonth.of(2026, 5), 23, PaydayAdjustment.NEXT_BUSINESS_DAY, NO_HOLIDAYS))
                .isEqualTo(LocalDate.of(2026, 5, 25));
    }

    @Test
    void businessDayIsUnchanged() {
        // 평일(5/25 월)은 어떤 규칙이든 그대로.
        assertThat(PaydayResolver.resolve(YearMonth.of(2026, 5), 25, PaydayAdjustment.PREV_BUSINESS_DAY, NO_HOLIDAYS))
                .isEqualTo(LocalDate.of(2026, 5, 25));
    }

    @Test
    void skipsConsecutiveHolidaysAndWeekend() {
        // 금공휴(9/25) + 주말 + 월공휴(9/28) → NEXT → 화(9/29).
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 28));
        assertThat(PaydayResolver.resolve(YearMonth.of(2026, 9), 25, PaydayAdjustment.NEXT_BUSINESS_DAY, holidays))
                .isEqualTo(LocalDate.of(2026, 9, 29));
    }

    @Test
    void adjustmentCanCrossMonthBoundary() {
        // 말일 clamp(2/28 토) → NEXT → 다음 달 월요일(3/2).
        assertThat(PaydayResolver.resolve(YearMonth.of(2026, 2), 31, PaydayAdjustment.NEXT_BUSINESS_DAY, NO_HOLIDAYS))
                .isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    void emptyHolidaySetAvoidsWeekendsOnly() {
        // 차년도 미제공 폴백 — 평일 공휴일(1/1 목)이어도 집합이 비면 그대로.
        assertThat(PaydayResolver.resolve(YearMonth.of(2026, 1), 1, PaydayAdjustment.NEXT_BUSINESS_DAY, NO_HOLIDAYS))
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void rejectsPaydayOutOfRange() {
        assertThatThrownBy(() -> PaydayResolver.resolve(YearMonth.of(2026, 5), 0, PaydayAdjustment.NONE, NO_HOLIDAYS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaydayResolver.resolve(YearMonth.of(2026, 5), 32, PaydayAdjustment.NONE, NO_HOLIDAYS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> PaydayResolver.resolve(null, 25, PaydayAdjustment.NONE, NO_HOLIDAYS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PaydayResolver.resolve(YearMonth.of(2026, 5), 25, null, NO_HOLIDAYS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PaydayResolver.resolve(YearMonth.of(2026, 5), 25, PaydayAdjustment.NONE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
