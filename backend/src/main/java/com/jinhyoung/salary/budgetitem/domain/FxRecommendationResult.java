package com.jinhyoung.salary.budgetitem.domain;

import java.math.BigDecimal;

/**
 * 외화 적립 도우미 결과(ITEM-04). 권장 월 이체액은 원 단위 long(1,000원 단위 올림 — 구현규칙 1장).
 * 버퍼율은 화면이 "버퍼 N% 포함" 고지를 표시하도록 함께 돌려준다(저장은 원화 월액만, 요구사항정의서 ITEM-04).
 *
 * @param recommendedMonthlyKrw 권장 월 이체액(원, 1,000원 단위 올림)
 * @param bufferRate 적용한 버퍼율(예: 0.05)
 */
public record FxRecommendationResult(long recommendedMonthlyKrw, BigDecimal bufferRate) {}
