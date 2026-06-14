package com.jinhyoung.salary.cycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 사이클 경계 산출(CYCLE-02) 단위 테스트. 공휴일을 직접 주입해 결정론적으로 검증한다. 기대 날짜의 요일은
 * 2026 실달력 기준이다(예: 2026-01-01 목, 02-01 일, 02-28 토, 03-31 화).
 */
class CycleResolverTest {

    @Test
    void 사이클은_실지급일부터_다음_지급일_전날까지다() {
        // 월급일 25일, 조정 없음 — 1월 사이클은 1/25부터 다음 지급일(2/25) 전날 2/24까지.
        CycleDefinition cycle = CycleResolver.resolve(YearMonth.of(2026, 1), 25, PaydayAdjustment.NONE, Set.of());

        assertThat(cycle.cycleStart()).isEqualTo(LocalDate.of(2026, 1, 25));
        assertThat(cycle.cycleEnd()).isEqualTo(LocalDate.of(2026, 2, 24));
        assertThat(cycle.label()).isEqualTo("2026-01");
    }

    @Test
    void 연속한_두_사이클의_경계가_맞닿는다() {
        CycleDefinition jan = CycleResolver.resolve(YearMonth.of(2026, 1), 25, PaydayAdjustment.NONE, Set.of());
        CycleDefinition feb = CycleResolver.resolve(YearMonth.of(2026, 2), 25, PaydayAdjustment.NONE, Set.of());

        // 1월 사이클 마지막 날 + 1일 = 2월 사이클 첫날 — 빈틈·중복 없는 연속.
        assertThat(jan.cycleEnd().plusDays(1)).isEqualTo(feb.cycleStart());
        assertThat(feb.label()).isEqualTo("2026-02");
    }

    @Test
    void 월말_월급일은_말일로_clamp한_뒤_조정한다() {
        // 월급일 31일의 2026-02는 말일 2/28(토)로 clamp → PREV이면 직전 영업일 2/27(금).
        // 다음 달 3/31(화)은 평일이라 PREV 무조정 → 경계 끝은 3/30.
        CycleDefinition cycle =
                CycleResolver.resolve(YearMonth.of(2026, 2), 31, PaydayAdjustment.PREV_BUSINESS_DAY, Set.of());

        assertThat(cycle.cycleStart()).isEqualTo(LocalDate.of(2026, 2, 27));
        assertThat(cycle.cycleEnd()).isEqualTo(LocalDate.of(2026, 3, 30));
        assertThat(cycle.label()).isEqualTo("2026-02");
    }

    @Test
    void 공휴일이면_시작일이_공휴일을_피해_이동한다() {
        // 2026-01-01(목, 신정)은 평일이지만 공휴일 — NEXT이면 다음 영업일 1/2(금)로 이동.
        CycleDefinition cycle = CycleResolver.resolve(
                YearMonth.of(2026, 1), 1, PaydayAdjustment.NEXT_BUSINESS_DAY, Set.of(LocalDate.of(2026, 1, 1)));

        assertThat(cycle.cycleStart()).isEqualTo(LocalDate.of(2026, 1, 2));
        // 다음 지급일 2/1(일) → NEXT → 2/2(월), 경계 끝은 그 전날 2/1.
        assertThat(cycle.cycleEnd()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(cycle.label()).isEqualTo("2026-01");
    }

    @Test
    void 연휴는_연속한_공휴일을_모두_건너뛴다() {
        // 1/1(목)·1/2(금) 연휴 + 1/3(토)·1/4(일) 주말 → NEXT는 1/5(월)까지 건너뛴다.
        CycleDefinition cycle = CycleResolver.resolve(
                YearMonth.of(2026, 1),
                1,
                PaydayAdjustment.NEXT_BUSINESS_DAY,
                Set.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)));

        assertThat(cycle.cycleStart()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(cycle.label()).isEqualTo("2026-01");
    }

    @Test
    void 조정이_전월로_넘어가도_라벨은_명목_시작월을_유지한다() {
        // 월급일 1일 + PREV + 신정(1/1 목) → 직전 영업일 2025-12-31(수)로 전월로 밀린다.
        // 그래도 라벨은 명목 시작월 "2026-01" — 12월 사이클과 라벨이 충돌하지 않게.
        CycleDefinition cycle = CycleResolver.resolve(
                YearMonth.of(2026, 1), 1, PaydayAdjustment.PREV_BUSINESS_DAY, Set.of(LocalDate.of(2026, 1, 1)));

        assertThat(cycle.cycleStart()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(cycle.label()).isEqualTo("2026-01");
    }

    @Test
    void startMonth가_null이면_거부한다() {
        assertThatThrownBy(() -> CycleResolver.resolve(null, 25, PaydayAdjustment.NONE, Set.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void cycleEnd가_cycleStart보다_앞서면_정의가_거부된다() {
        assertThatThrownBy(() -> new CycleDefinition(LocalDate.of(2026, 1, 25), LocalDate.of(2026, 1, 24), "2026-01"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
