package com.jinhyoung.salary.suggestion.domain;

import java.time.LocalDate;

/**
 * 만기 도래 후보 항목(SUG-01 리밸런싱 룰 입력). 의존성 없는 순수 값(규칙 9) — 만기일이 다가오면 해제될 월 납입액
 * ({@code monthlyAmount})을 다른 항목으로 재배치하라고 제안한다.
 *
 * <p>{@code expectedMaturityAmount}는 예상 만기금액(ITEM-05/06 {@code ExpectedMaturity} 해석값)으로, 공식 계산도
 * 수동값도 없으면 {@code null}일 수 있다(제안은 여전히 월 납입액 재배치 관점에서 유효하다). 금액은 long 원 단위(규칙 2).
 *
 * @param itemId 항목 id(제안 dedup 키·payload에 사용)
 * @param itemName 항목 이름(스냅샷 — 제안 표시용)
 * @param monthlyAmount 만기 시 해제될 월 납입액(원)
 * @param expectedMaturityAmount 예상 만기금액(원). 산출 불가 시 {@code null}
 * @param maturityDate 만기일(end_date)
 */
public record MaturingItem(
        long itemId, String itemName, long monthlyAmount, Long expectedMaturityAmount, LocalDate maturityDate) {}
