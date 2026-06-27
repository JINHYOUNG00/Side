package com.jinhyoung.salary.budgetitem.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 외화 적립 도우미 — 권장 월 이체액 계산, 의존성 없는 순수 클래스(ITEM-04, 구현규칙 1장·6장).
 *
 * <p>일/회 외화 금액에 빈도별 월 평균 일수와 기준 환율을 곱해 월 원화 금액을 구하고, 버퍼율을 더한 뒤
 * <b>1,000원 단위로 올림</b>한다(구현규칙 1장: 222,541 → 223,000):
 *
 * <pre>
 *   월 원화(버퍼 전) = unitAmount × frequency.daysPerMonth × fxRate
 *   권장 월 이체액   = ceil1000( 월 원화 × (1 + bufferRate) )
 * </pre>
 *
 * 중간 계산은 {@link BigDecimal}로 하고 마지막에만 long 원으로 절단한다(double/float 금지 — 규칙 2). 환율
 * 변동·달력 일수 변동은 버퍼가 흡수하며 실시간 환율 연동·일별 추적은 하지 않는다(요구사항정의서 ITEM-04).
 */
public final class FxRecommendationCalculator {

    /** 권장 월 이체액의 올림 단위(원) — 구현규칙 1장. */
    private static final BigDecimal ROUNDING_UNIT = BigDecimal.valueOf(1000);

    private FxRecommendationCalculator() {}

    public static FxRecommendationResult calculate(FxRecommendationInput in) {
        BigDecimal monthlyKrw = in.unitAmount()
                .multiply(BigDecimal.valueOf(in.frequency().daysPerMonth()))
                .multiply(in.fxRate())
                .multiply(BigDecimal.ONE.add(in.bufferRate()));

        // 1,000원 단위 올림: ⌈x / 1000⌉ × 1000.
        long recommended = monthlyKrw
                .divide(ROUNDING_UNIT, 0, RoundingMode.CEILING)
                .multiply(ROUNDING_UNIT)
                .longValueExact();

        return new FxRecommendationResult(recommended, in.bufferRate());
    }
}
