package com.jinhyoung.salary.budgetitem.domain;

/**
 * 항목 금액 입력 단위(ERD budget_items.input_cycle, ITEM-03). 순수 enum — 프레임워크 의존 없음(규칙 9).
 *
 * <ul>
 *   <li>{@code MONTHLY}(기본) — 월 환산 금액을 그대로 입력한다. input_meta 없음.
 *   <li>{@code DAILY} — 매일 적립식 항목. 일 금액과 빈도(매일/영업일)를 입력하면 월 환산 금액을 자동 계산하고
 *       (서버 권위), 원본 일 금액·빈도는 input_meta에 보존한다({@link DailyInput}).
 * </ul>
 *
 * <p>저장 컬럼 amount는 두 경우 모두 <b>월 환산 금액(원)</b>이다 — 폭포·스냅샷은 amount만 보므로 입력 단위와
 * 무관하게 일관된다(ERD: "amount = 월 환산 금액"). DAILY일 때 amount는 {@link DailyInput#toMonthlyAmount()}로
 * 도출한다.
 */
public enum InputCycle {
    MONTHLY,
    DAILY
}
