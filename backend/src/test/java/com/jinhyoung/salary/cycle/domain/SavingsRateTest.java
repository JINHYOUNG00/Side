package com.jinhyoung.salary.cycle.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhyoung.salary.budgetitem.domain.Category;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 저축률 산정(SET-02) 단위 테스트. 순수 도메인 {@link SavingsRate} — 저축액(SAVING + 토글 시 INVESTMENT) ÷
 * 수입을 소수 첫째 자리로 반올림한다. EMERGENCY·LIVING은 애초에 groups에 없으므로 입력에 넣지 않는다.
 */
class SavingsRateTest {

    private static WaterfallGroup group(Category category, long subtotal) {
        return new WaterfallGroup(category, subtotal, List.of());
    }

    @Test
    void 투자_포함이면_SAVING과_INVESTMENT_합을_수입으로_나눈다() {
        // 노션 실데이터: (700,000 + 800,000) / 2,473,110 = 60.6519…% → 60.7
        List<WaterfallGroup> groups = List.of(
                group(Category.SAVING, 700_000), group(Category.INVESTMENT, 800_000), group(Category.FIXED, 310_600));

        SavingsRate rate = SavingsRate.from(groups, 2_473_110, true);

        assertThat(rate.value()).isEqualByComparingTo(new BigDecimal("60.7"));
        assertThat(rate.includesInvestment()).isTrue();
    }

    @Test
    void 투자_제외면_SAVING만_저축액으로_본다() {
        // 700,000 / 2,473,110 = 28.305…% → 28.3
        List<WaterfallGroup> groups = List.of(group(Category.SAVING, 700_000), group(Category.INVESTMENT, 800_000));

        SavingsRate rate = SavingsRate.from(groups, 2_473_110, false);

        assertThat(rate.value()).isEqualByComparingTo(new BigDecimal("28.3"));
        assertThat(rate.includesInvestment()).isFalse();
    }

    @Test
    void 저축_투자가_아닌_카테고리는_저축액에서_제외된다() {
        List<WaterfallGroup> groups = List.of(
                group(Category.FIXED, 500_000),
                group(Category.INSURANCE, 100_000),
                group(Category.SUBSCRIPTION, 10_000));

        SavingsRate rate = SavingsRate.from(groups, 1_000_000, true);

        assertThat(rate.value()).isEqualByComparingTo(new BigDecimal("0.0"));
    }

    @Test
    void 항목이_없으면_저축률은_0이다() {
        SavingsRate rate = SavingsRate.from(List.of(), 3_000_000, true);

        assertThat(rate.value()).isEqualByComparingTo(new BigDecimal("0.0"));
    }

    @Test
    void 수입이_0이면_나눗셈_없이_0을_반환한다() {
        List<WaterfallGroup> groups = List.of(group(Category.SAVING, 500_000));

        SavingsRate rate = SavingsRate.from(groups, 0, true);

        assertThat(rate.value()).isEqualByComparingTo(new BigDecimal("0.0"));
    }

    @Test
    void 소수_첫째_자리에서_반올림한다() {
        // 2 / 3 = 66.666…% → 66.7 (HALF_UP)
        SavingsRate up = SavingsRate.from(List.of(group(Category.SAVING, 2)), 3, false);
        assertThat(up.value()).isEqualByComparingTo(new BigDecimal("66.7"));

        // 1 / 3 = 33.333…% → 33.3
        SavingsRate down = SavingsRate.from(List.of(group(Category.SAVING, 1)), 3, false);
        assertThat(down.value()).isEqualByComparingTo(new BigDecimal("33.3"));
    }

    @Test
    void 저축률은_소수_첫째_자리_스케일을_유지한다() {
        // 정확히 떨어져도 60 이 아니라 60.0 으로 표기(스케일 1).
        SavingsRate rate = SavingsRate.from(List.of(group(Category.SAVING, 600_000)), 1_000_000, false);

        assertThat(rate.value().scale()).isEqualTo(1);
        assertThat(rate.value()).isEqualByComparingTo(new BigDecimal("60.0"));
    }
}
