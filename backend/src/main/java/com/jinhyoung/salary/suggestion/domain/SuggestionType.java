package com.jinhyoung.salary.suggestion.domain;

/**
 * 보정/리밸런싱 제안 종류(SUG-01·SUG-02, ERD suggestions.type). 순수 enum — 프레임워크 의존 없음(규칙 9).
 *
 * <ul>
 *   <li>{@link #RAISE_LIVING} — 직전 연속 사이클이 계획보다 더 썼을 때(overspend &gt; 0) 생활비 증액 제안(SUG-02)
 *   <li>{@link #RAISE_SAVING} — 직전 연속 사이클이 계획보다 덜 썼을 때(잉여) 저축 증액 제안(SUG-02)
 *   <li>{@link #REBALANCE_MATURITY} — 만기 1개월 전, 해제될 월 납입액의 재배치 제안(SUG-01)
 *   <li>{@link #WINDFALL} — 실수령액이 평소보다 기준 이상 많을 때, 여윳돈 차액의 배분처 검토 제안(CYCLE-05)
 *   <li>{@link #SHORTFALL} — 실수령액이 평소보다 기준 이상 적을 때, 축소 대상 검토 제안(CYCLE-05 역방향)
 * </ul>
 */
public enum SuggestionType {
    RAISE_LIVING,
    RAISE_SAVING,
    REBALANCE_MATURITY,
    WINDFALL,
    SHORTFALL
}
