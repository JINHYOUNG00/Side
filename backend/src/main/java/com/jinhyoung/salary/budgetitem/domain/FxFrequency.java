package com.jinhyoung.salary.budgetitem.domain;

/**
 * 외화 적립 빈도(ITEM-04). 일/회 외화 금액이 한 달에 몇 번 발생하는지를 월 평균 일수로 환산한다.
 *
 * <p>요구사항은 환율 API 연동·일별 차감 추적을 하지 않으므로(요구사항정의서 ITEM-04) 실제 달력 일수가
 * 아닌 <b>월 평균 근사값</b>으로 월 이체액을 산정한다. 변동분은 버퍼(구현규칙 6장 fx-buffer-rate)가 흡수한다.
 *
 * <ul>
 *   <li>{@code DAILY}(매일) = 30일 — 달력일 월 평균 근사
 *   <li>{@code BUSINESS_DAYS}(영업일) = 22일 — 주말 제외 월 평균 근사(공휴일은 미추적)
 * </ul>
 */
public enum FxFrequency {
    DAILY(30),
    BUSINESS_DAYS(22);

    private final int daysPerMonth;

    FxFrequency(int daysPerMonth) {
        this.daysPerMonth = daysPerMonth;
    }

    /** 월 평균 발생 일수(근사). 월 이체액 = 일 외화 금액 × 이 값 × 환율 × (1 + 버퍼). */
    public int daysPerMonth() {
        return daysPerMonth;
    }
}
