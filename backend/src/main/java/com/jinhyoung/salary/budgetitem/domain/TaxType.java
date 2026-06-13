package com.jinhyoung.salary.budgetitem.domain;

import java.math.BigDecimal;

/**
 * 적금 이자 과세 유형. 세율은 이자에 곱하는 비율(원 단위 반올림은 계산기 책임).
 * 금액·비율은 절대 double 금지 — BigDecimal (CLAUDE.md 규칙 2).
 */
public enum TaxType {
    /** 일반과세 15.4% (소득세 14% + 지방세 1.4%). */
    NORMAL_15_4(new BigDecimal("0.154")),
    /** 비과세 — 세금 0. */
    TAX_FREE(BigDecimal.ZERO);

    private final BigDecimal rate;

    TaxType(BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal rate() {
        return rate;
    }
}
