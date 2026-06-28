package com.jinhyoung.salary.budgetitem.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * DailyInput 단위 테스트(ITEM-03) — 일 금액 → 월 환산(빈도별 월 평균 일수), 정수 곱(반올림 없음), 입력 검증.
 * 순수 JUnit/assertj만 사용(도메인 순수성 유지, 규칙 9). 금액은 long 원(규칙 2).
 */
class DailyInputTest {

    @Test
    void 매일_빈도는_월30일로_환산한다() {
        // 10,000원 × 30일 = 300,000원.
        assertThat(new DailyInput(10_000, FxFrequency.DAILY).toMonthlyAmount()).isEqualTo(300_000);
    }

    @Test
    void 영업일_빈도는_월22일로_환산한다() {
        // 10,000원 × 22영업일 = 220,000원.
        assertThat(new DailyInput(10_000, FxFrequency.BUSINESS_DAYS).toMonthlyAmount())
                .isEqualTo(220_000);
    }

    @Test
    void 정수_곱이라_반올림이_없다() {
        // 3,333원 × 30일 = 99,990원(외화와 달리 1,000원 올림 없음 — 정확히 곱만).
        assertThat(new DailyInput(3_333, FxFrequency.DAILY).toMonthlyAmount()).isEqualTo(99_990);
    }

    @Test
    void 일금액이_0이하면_거부된다() {
        assertThatThrownBy(() -> new DailyInput(0, FxFrequency.DAILY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyInput(-1, FxFrequency.BUSINESS_DAYS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 빈도가_null이면_거부된다() {
        assertThatThrownBy(() -> new DailyInput(10_000, null)).isInstanceOf(NullPointerException.class);
    }
}
