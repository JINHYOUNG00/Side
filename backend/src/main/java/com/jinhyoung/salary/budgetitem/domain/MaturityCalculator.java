package com.jinhyoung.salary.budgetitem.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 적금 만기금액 계산 — 단리, 의존성 없는 순수 클래스(ITEM-05, 구현규칙 1장).
 *
 * <p>매월 같은 금액을 납입하는 정기적금의 단리 이자: n개월 납입 시 첫 달 납입금은
 * n개월, 둘째 달은 (n−1)개월 … 마지막 달은 1개월치 이자를 받는다. 따라서
 * <pre>
 *   이자(세전) = 월납입액 × 연이율 × n(n+1)/2 ÷ 1200      (÷100: %→비율, ÷12: 월 환산)
 * </pre>
 * 이자를 원 미만 반올림한 뒤 세금(taxType 세율)을 적용하고 다시 원 미만 반올림한다
 * (구현규칙 1장의 2단계 반올림). 은행 실수령과 수원 단위 차이가 날 수 있다(ITEM-05 고지).
 */
public final class MaturityCalculator {

    /** ÷100(%→비율) × ÷12(연→월)을 한 번에 적용하는 제수. */
    private static final BigDecimal RATE_DIVISOR = BigDecimal.valueOf(1200);

    private MaturityCalculator() {}

    public static MaturityResult calculate(MaturityInput in) {
        long principal = Math.multiplyExact(in.monthlyAmount(), in.months());

        // n(n+1)/2 — long 범위로 충분(months는 검증상 작은 정수).
        long sumFactor = (long) in.months() * (in.months() + 1) / 2;

        BigDecimal interestRaw = BigDecimal.valueOf(in.monthlyAmount())
                .multiply(in.annualRatePct())
                .multiply(BigDecimal.valueOf(sumFactor));
        long interest =
                interestRaw.divide(RATE_DIVISOR, 0, RoundingMode.HALF_UP).longValueExact();

        long tax = BigDecimal.valueOf(interest)
                .multiply(in.taxType().rate())
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        return new MaturityResult(principal, interest, tax, principal + interest - tax);
    }
}
