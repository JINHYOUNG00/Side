package com.jinhyoung.salary.budgetitem.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * FxRecommendationCalculator 단위 테스트(ITEM-04) — 빈도별 월 평균 일수 환산, 버퍼 적용, 1,000원 단위 올림
 * (구현규칙 1장), BigDecimal 정밀도, 입력 검증. 순수 JUnit/assertj만 사용(도메인 순수성 유지).
 */
class FxRecommendationCalculatorTest {

    private static final BigDecimal BUFFER_5 = new BigDecimal("0.05");
    private static final BigDecimal NO_BUFFER = BigDecimal.ZERO;

    @Test
    void 영업일_빈도는_월22일로_환산하고_버퍼와_올림을_적용한다() {
        // $7 × 22영업일 × ₩1,380 = 212,520 → ×1.05 = 223,146 → 1,000원 올림 → 224,000.
        FxRecommendationResult r = FxRecommendationCalculator.calculate(new FxRecommendationInput(
                new BigDecimal("7"), FxFrequency.BUSINESS_DAYS, new BigDecimal("1380"), BUFFER_5));

        assertThat(r.recommendedMonthlyKrw()).isEqualTo(224_000);
        assertThat(r.bufferRate()).isEqualByComparingTo(BUFFER_5);
    }

    @Test
    void 매일_빈도는_월30일로_환산한다() {
        // $10 × 30일 × ₩1,300 = 390,000 → ×1.05 = 409,500 → 1,000원 올림 → 410,000.
        FxRecommendationResult r = FxRecommendationCalculator.calculate(
                new FxRecommendationInput(new BigDecimal("10"), FxFrequency.DAILY, new BigDecimal("1300"), BUFFER_5));

        assertThat(r.recommendedMonthlyKrw()).isEqualTo(410_000);
    }

    @Test
    void 천원_단위로_올림한다() {
        // $1 × 30일 × ₩7,401 = 222,030(버퍼 0) → 1,000원 올림 → 223,000(내림 222,000이 아니다).
        FxRecommendationResult r = FxRecommendationCalculator.calculate(
                new FxRecommendationInput(new BigDecimal("1"), FxFrequency.DAILY, new BigDecimal("7401"), NO_BUFFER));

        assertThat(r.recommendedMonthlyKrw()).isEqualTo(223_000);
    }

    @Test
    void 정확히_천원_배수면_올리지_않는다() {
        // $1 × 30일 × ₩10,000 = 300,000(버퍼 0) → 이미 1,000원 배수라 그대로.
        FxRecommendationResult r = FxRecommendationCalculator.calculate(
                new FxRecommendationInput(new BigDecimal("1"), FxFrequency.DAILY, new BigDecimal("10000"), NO_BUFFER));

        assertThat(r.recommendedMonthlyKrw()).isEqualTo(300_000);
    }

    @Test
    void 소수_금액과_환율도_정밀하게_계산한다() {
        // $7.5 × 30일 × ₩1,380.5 = 310,612.5 → ×1.05 = 326,143.125 → 1,000원 올림 → 327,000(double 오차 없음).
        FxRecommendationResult r = FxRecommendationCalculator.calculate(new FxRecommendationInput(
                new BigDecimal("7.5"), FxFrequency.DAILY, new BigDecimal("1380.5"), BUFFER_5));

        assertThat(r.recommendedMonthlyKrw()).isEqualTo(327_000);
    }

    @Test
    void 빈도별_월평균_일수() {
        assertThat(FxFrequency.DAILY.daysPerMonth()).isEqualTo(30);
        assertThat(FxFrequency.BUSINESS_DAYS.daysPerMonth()).isEqualTo(22);
    }

    @Test
    void 잘못된_입력은_거부된다() {
        assertThatThrownBy(() ->
                        new FxRecommendationInput(BigDecimal.ZERO, FxFrequency.DAILY, new BigDecimal("1380"), BUFFER_5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new FxRecommendationInput(new BigDecimal("7"), FxFrequency.DAILY, BigDecimal.ZERO, BUFFER_5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FxRecommendationInput(
                        new BigDecimal("7"), FxFrequency.DAILY, new BigDecimal("1380"), new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
