package com.jinhyoung.salary.cycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jinhyoung.salary.budgetitem.domain.Category;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * WaterfallCalculator 단위 테스트(FLOW-01) — 골든(노션 실데이터) 이전의 경계: 빈 항목, 카테고리
 * 누락, 초과 배분(remaining 음수), EMERGENCY 제외, 표시 순서, 입력 검증, 오버플로. 순수
 * JUnit/assertj만 사용(도메인 순수성 유지). 실데이터 폭포 기대값은 HARNESS-golden(소유자 lock) 소관.
 */
class WaterfallCalculatorTest {

    private static WaterfallLine line(long id, Category category, long amount) {
        return new WaterfallLine(id, category, amount);
    }

    @Test
    void 빈_항목이면_그룹이_없고_remaining은_income과_같다() {
        WaterfallResult r = WaterfallCalculator.calculate(2_500_000, List.of(), 0);

        assertThat(r.groups()).isEmpty();
        assertThat(r.remaining()).isEqualTo(2_500_000);
        assertThat(r.emergencyTotal()).isZero();
        assertThat(r.envelopeContribution()).isZero();
    }

    @Test
    void 존재하는_카테고리만_그룹이_되고_누락_카테고리는_생략된다() {
        WaterfallResult r = WaterfallCalculator.calculate(
                2_500_000, List.of(line(1, Category.SAVING, 700_000), line(2, Category.FIXED, 280_000)), 0);

        assertThat(r.groups()).extracting(WaterfallGroup::category).containsExactly(Category.SAVING, Category.FIXED);
        assertThat(r.remaining()).isEqualTo(2_500_000 - 700_000 - 280_000);
    }

    @Test
    void 같은_카테고리_다항목은_소계로_합쳐지고_라인_순서를_보존한다() {
        WaterfallResult r = WaterfallCalculator.calculate(
                3_000_000,
                List.of(
                        line(1, Category.SAVING, 700_000),
                        line(2, Category.SAVING, 300_000),
                        line(3, Category.SAVING, 100_000)),
                0);

        assertThat(r.groups()).hasSize(1);
        WaterfallGroup saving = r.groups().get(0);
        assertThat(saving.subtotal()).isEqualTo(1_100_000);
        assertThat(saving.lines()).extracting(WaterfallLine::budgetItemId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 배분이_수입을_초과하면_remaining은_음수다_clamp하지_않는다() {
        WaterfallResult r = WaterfallCalculator.calculate(
                1_000_000, List.of(line(1, Category.SAVING, 700_000), line(2, Category.FIXED, 500_000)), 0);

        assertThat(r.remaining()).isEqualTo(1_000_000 - 700_000 - 500_000);
        assertThat(r.remaining()).isNegative();
    }

    @Test
    void EMERGENCY는_그룹에서_빠지고_remaining에서_미차감되며_emergencyTotal로_집계된다() {
        WaterfallResult r = WaterfallCalculator.calculate(
                2_000_000, List.of(line(1, Category.SAVING, 700_000), line(2, Category.EMERGENCY, 200_000)), 0);

        assertThat(r.groups()).extracting(WaterfallGroup::category).containsExactly(Category.SAVING);
        // remaining은 SAVING만 차감(EMERGENCY 미차감) — 200,000은 remaining 안에 남는다.
        assertThat(r.remaining()).isEqualTo(2_000_000 - 700_000);
        assertThat(r.emergencyTotal()).isEqualTo(200_000);
    }

    @Test
    void envelopeContribution은_remaining에서_차감된다() {
        WaterfallResult r =
                WaterfallCalculator.calculate(2_000_000, List.of(line(1, Category.SAVING, 700_000)), 40_000);

        assertThat(r.remaining()).isEqualTo(2_000_000 - 700_000 - 40_000);
        assertThat(r.envelopeContribution()).isEqualTo(40_000);
    }

    @Test
    void 그룹은_입력_순서와_무관하게_표시_순서로_정렬된다() {
        WaterfallResult r = WaterfallCalculator.calculate(
                3_000_000,
                List.of(
                        line(1, Category.FIXED, 280_000),
                        line(2, Category.SAVING, 700_000),
                        line(3, Category.INVESTMENT, 800_000)),
                0);

        assertThat(r.groups())
                .extracting(WaterfallGroup::category)
                .containsExactly(Category.SAVING, Category.INVESTMENT, Category.FIXED);
    }

    @Test
    void 배분과_봉투_합이_수입과_같으면_remaining은_0이다() {
        WaterfallResult r = WaterfallCalculator.calculate(
                1_000_000, List.of(line(1, Category.SAVING, 700_000), line(2, Category.FIXED, 260_000)), 40_000);

        assertThat(r.remaining()).isZero();
    }

    @Test
    void 잘못된_입력은_거부된다() {
        // 라인 금액 ≤ 0
        assertThatThrownBy(() -> line(1, Category.SAVING, 0)).isInstanceOf(IllegalArgumentException.class);
        // budgetItemId ≤ 0
        assertThatThrownBy(() -> line(0, Category.SAVING, 100_000)).isInstanceOf(IllegalArgumentException.class);
        // null 카테고리
        assertThatThrownBy(() -> new WaterfallLine(1, null, 100_000)).isInstanceOf(NullPointerException.class);
        // income 음수
        assertThatThrownBy(() -> WaterfallCalculator.calculate(-1, List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        // envelopeContribution 음수
        assertThatThrownBy(() -> WaterfallCalculator.calculate(1_000_000, List.of(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 소계_합산이_long_범위를_넘으면_ArithmeticException() {
        assertThatThrownBy(() -> WaterfallCalculator.calculate(
                        Long.MAX_VALUE,
                        List.of(line(1, Category.SAVING, Long.MAX_VALUE), line(2, Category.SAVING, Long.MAX_VALUE)),
                        0))
                .isInstanceOf(ArithmeticException.class);
    }
}
