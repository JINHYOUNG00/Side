package com.jinhyoung.salary.budgetitem.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 예상 만기금액 해석(ITEM-05·06) — 의존성 없는 순수 클래스. 항목의 저축 조건부 필드로부터 표시용 예상
 * 만기금액(원, long)을 도출한다. 결과는 "예상치"이며 은행 실수령과 수원 단위 차이가 날 수 있다(ITEM-05 고지).
 *
 * <p>해석 규칙(ERD: expected_maturity_amount "있으면 공식 계산 대신 사용", 계산값 비저장 원칙):
 *
 * <ul>
 *   <li>수동 입력값({@code manualOverride})이 있으면 그 값을 그대로 쓴다 — 청년도약계좌 등 표준 공식이
 *       적용되지 않는 특수 상품(ITEM-06).
 *   <li>그 외에는 SAVING 항목이고 연이율·세금유형·시작일·만기일이 모두 있을 때만 단리 공식으로 계산한다
 *       (ITEM-05). 한 가지라도 없으면(또는 다른 카테고리이면) null — 예상금액을 표시하지 않는다.
 * </ul>
 *
 * <p>납입 개월 수는 시작일~만기일 구간으로 도출한다. 만기일 당일까지 납입한 것으로 보아 만기일 다음 날까지의
 * 월 수로 센다(end-inclusive) — 만기일을 마지막 날(예: 2027-06-30)로 두든 다음 사이클 첫날(2027-07-01)로
 * 두든 12개월로 일치한다. 1개월 미만이면 계산하지 않는다(null).
 */
public final class ExpectedMaturity {

    private ExpectedMaturity() {}

    public static Long resolve(
            Category category,
            long monthlyAmount,
            BigDecimal annualRatePct,
            LocalDate startDate,
            LocalDate endDate,
            TaxType taxType,
            Long manualOverride) {
        if (manualOverride != null) {
            return manualOverride; // ITEM-06: 특수 상품 수동 입력값 우선
        }
        if (category != Category.SAVING
                || annualRatePct == null
                || taxType == null
                || startDate == null
                || endDate == null) {
            return null;
        }
        long months = ChronoUnit.MONTHS.between(startDate, endDate.plusDays(1));
        if (months < 1) {
            return null;
        }
        return MaturityCalculator.calculate(new MaturityInput(monthlyAmount, annualRatePct, (int) months, taxType))
                .total();
    }
}
