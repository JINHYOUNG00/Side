package com.jinhyoung.salary.budgetitem.domain;

/**
 * 적금 만기 계산 결과(전부 원 단위 long). total = principal + interest − tax.
 * ITEM-05 화면이 원금·이자·세금 분해를 표시할 수 있도록 분해값을 함께 제공한다.
 *
 * @param principal 원금(월 납입액 × 개월)
 * @param interest 세전 이자(원 미만 반올림)
 * @param tax 이자과세(원 미만 반올림)
 * @param total 만기 실수령액
 */
public record MaturityResult(long principal, long interest, long tax, long total) {}
