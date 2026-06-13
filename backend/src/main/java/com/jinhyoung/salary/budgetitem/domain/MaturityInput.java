package com.jinhyoung.salary.budgetitem.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 적금 만기 계산 입력 — 엔티티가 아닌 순수 값 객체(아키텍처.md 2장).
 *
 * @param monthlyAmount 월 납입액(원, long)
 * @param annualRatePct 연이율(%) — 예: 8.0. double 금지라 BigDecimal
 * @param months 납입 개월 수(≥1)
 * @param taxType 과세 유형
 */
public record MaturityInput(long monthlyAmount, BigDecimal annualRatePct, int months, TaxType taxType) {

    public MaturityInput {
        Objects.requireNonNull(annualRatePct, "annualRatePct");
        Objects.requireNonNull(taxType, "taxType");
        if (monthlyAmount <= 0) {
            throw new IllegalArgumentException("monthlyAmount는 0보다 커야 한다: " + monthlyAmount);
        }
        if (months < 1) {
            throw new IllegalArgumentException("months는 1 이상이어야 한다: " + months);
        }
        if (annualRatePct.signum() < 0) {
            throw new IllegalArgumentException("annualRatePct는 음수일 수 없다: " + annualRatePct);
        }
    }
}
