package com.jinhyoung.salary.budgetitem.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * MaturityCalculator 단위 테스트 — 골든(실데이터)이 다루지 않는 경계: 비과세,
 * 세금 2단계 반올림(HALF_UP), 입력 검증. 순수 JUnit/assertj만 사용(도메인 순수성 유지).
 */
class MaturityCalculatorTest {

    @Test
    void 비과세는_세금이_0이고_total은_원금더하기이자() {
        // 월 10만 · 연 8% · 12개월: 이자 100000×8×78/1200 = 52,000
        MaturityResult r =
                MaturityCalculator.calculate(new MaturityInput(100_000, new BigDecimal("8.0"), 12, TaxType.TAX_FREE));

        assertThat(r.principal()).isEqualTo(1_200_000);
        assertThat(r.interest()).isEqualTo(52_000);
        assertThat(r.tax()).isZero();
        assertThat(r.total()).isEqualTo(1_252_000);
    }

    @Test
    void 세금은_원미만에서_HALF_UP_반올림된다() {
        // 월 10만 · 연 3% · 6개월: 이자 100000×3×21/1200 = 5,250
        // 세금 5250 × 0.154 = 808.5 → HALF_UP → 809
        MaturityResult r =
                MaturityCalculator.calculate(new MaturityInput(100_000, new BigDecimal("3.0"), 6, TaxType.NORMAL_15_4));

        assertThat(r.interest()).isEqualTo(5_250);
        assertThat(r.tax()).isEqualTo(809);
        assertThat(r.total()).isEqualTo(600_000 + 5_250 - 809);
    }

    @Test
    void 잘못된_입력은_거부된다() {
        assertThatThrownBy(() -> new MaturityInput(0, new BigDecimal("8.0"), 12, TaxType.NORMAL_15_4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MaturityInput(100_000, new BigDecimal("8.0"), 0, TaxType.NORMAL_15_4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MaturityInput(100_000, new BigDecimal("-1"), 12, TaxType.NORMAL_15_4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
