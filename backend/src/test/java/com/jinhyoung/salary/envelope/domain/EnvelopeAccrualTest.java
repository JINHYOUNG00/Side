package com.jinhyoung.salary.envelope.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 봉투 월할 적립 계산(ENV-02, 구현규칙 1장) 단위 테스트. 사이클 경계는 공휴일을 직접 주입해
 * 결정론적으로 검증한다(월급일 25일·조정 없음 기준, 2026~2027 실달력).
 */
class EnvelopeAccrualTest {

    // --- monthlyAmount: ceil 올림 ---

    @Test
    void 월할_적립액은_올림한다() {
        // (100,000 − 0) ÷ 3 = 33,333.3… → 올림 33,334
        assertThat(EnvelopeAccrual.monthlyAmount(100_000L, 0L, 3)).isEqualTo(33_334L);
    }

    @Test
    void 나누어떨어지면_올림_없이_그대로다() {
        assertThat(EnvelopeAccrual.monthlyAmount(90_000L, 0L, 3)).isEqualTo(30_000L);
    }

    @Test
    void 이미_적립한_금액은_차감하고_남은_잔액만_나눈다() {
        // 남은 잔액 (100,000 − 40,000) ÷ 4 = 15,000
        assertThat(EnvelopeAccrual.monthlyAmount(100_000L, 40_000L, 4)).isEqualTo(15_000L);
    }

    @Test
    void 남은_사이클이_1이면_잔여_전액을_적립한다() {
        // 마지막 달 = 잔여 전액(구현규칙 1장)
        assertThat(EnvelopeAccrual.monthlyAmount(50_000L, 30_000L, 1)).isEqualTo(20_000L);
    }

    @Test
    void 이미_목표를_충족했으면_0이다() {
        assertThat(EnvelopeAccrual.monthlyAmount(50_000L, 50_000L, 3)).isZero();
    }

    @Test
    void 초과_적립이어도_음수가_아니라_0이다() {
        assertThat(EnvelopeAccrual.monthlyAmount(50_000L, 60_000L, 3)).isZero();
    }

    @Test
    void 남은_사이클_수가_1_미만이면_거부한다() {
        assertThatThrownBy(() -> EnvelopeAccrual.monthlyAmount(100_000L, 0L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 올림으로_생긴_잉여는_매_사이클_재계산으로_자동_흡수된다() {
        // target 100,000을 3사이클에 걸쳐 적립 — 매 사이클 갱신된 saved로 재계산하면 정확히 100,000으로 수렴.
        long target = 100_000L;
        long saved = 0L;

        long c1 = EnvelopeAccrual.monthlyAmount(target, saved, 3); // ceil(100,000/3) = 33,334
        saved += c1;
        long c2 = EnvelopeAccrual.monthlyAmount(target, saved, 2); // ceil( 66,666/2) = 33,333
        saved += c2;
        long c3 = EnvelopeAccrual.monthlyAmount(target, saved, 1); // 잔여 전액 = 33,333
        saved += c3;

        assertThat(c1).isEqualTo(33_334L);
        assertThat(c2).isEqualTo(33_333L);
        assertThat(c3).isEqualTo(33_333L);
        assertThat(saved).isEqualTo(target); // 잉여 흡수 — 마지막 달에 부족분 없음
    }

    // --- remainingCycles: 사이클 개수 세기 ---

    @Test
    void 남은_사이클_수는_오늘_사이클부터_지출일_사이클까지_이번_포함이다() {
        // 구현규칙 1장 예시: 6월 사이클(6/25~)에 next_due 2027-01-10이면 6,7,8,9,10,11,12월 = 7
        int cycles = EnvelopeAccrual.remainingCycles(
                LocalDate.of(2026, 6, 25), LocalDate.of(2027, 1, 10), 25, PaydayAdjustment.NONE, Set.of());

        assertThat(cycles).isEqualTo(7);
    }

    @Test
    void 오늘이_지급일이_아닌_사이클_중간이어도_같은_사이클로_센다() {
        // 6월 사이클은 6/25~7/24 — 오늘 7/10도 6월 사이클이므로 결과는 동일하게 7.
        int cycles = EnvelopeAccrual.remainingCycles(
                LocalDate.of(2026, 7, 10), LocalDate.of(2027, 1, 10), 25, PaydayAdjustment.NONE, Set.of());

        assertThat(cycles).isEqualTo(7);
    }

    @Test
    void 지출일이_이번_사이클_안이면_1이다() {
        // 6월 사이클(6/25~7/24) 안에 지출일 7/5 → 남은 사이클 1.
        int cycles = EnvelopeAccrual.remainingCycles(
                LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 5), 25, PaydayAdjustment.NONE, Set.of());

        assertThat(cycles).isEqualTo(1);
    }

    @Test
    void 다음_사이클이_지출일이면_2다() {
        // 6월 사이클(6/25~7/24)·7월 사이클(7/25~) — 지출일 8/1은 7월 사이클 → 6,7월 = 2.
        int cycles = EnvelopeAccrual.remainingCycles(
                LocalDate.of(2026, 6, 25), LocalDate.of(2026, 8, 1), 25, PaydayAdjustment.NONE, Set.of());

        assertThat(cycles).isEqualTo(2);
    }

    @Test
    void 지출일이_과거여도_최소_1이다() {
        int cycles = EnvelopeAccrual.remainingCycles(
                LocalDate.of(2026, 6, 25), LocalDate.of(2026, 1, 10), 25, PaydayAdjustment.NONE, Set.of());

        assertThat(cycles).isEqualTo(1);
    }
}
