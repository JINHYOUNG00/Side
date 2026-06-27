package com.jinhyoung.salary.budgetitem.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 외화 적립 도우미 입력(ITEM-04) — 엔티티가 아닌 순수 값 객체. 일/회 외화 금액·빈도·기준 환율·버퍼율로
 * 권장 월 이체액(원)을 계산한다(요구사항정의서 ITEM-04, 구현규칙 1장·6장).
 *
 * <p>{@code unitAmount}·{@code fxRate}는 원화가 아니라 외화 금액·환율이므로 long 원 단위 규칙(규칙 2)의
 * 대상이 아니다 — 소수가 가능하고 정밀도 손실을 막아야 하므로 {@code double} 금지에 따라 {@link BigDecimal}로
 * 받는다. 통화 코드(currency)는 표시·보존용 메타라 계산에 쓰지 않아 입력에서 제외한다.
 *
 * @param unitAmount 1회(=하루) 외화 금액(예: 7 USD). 0보다 커야 한다
 * @param frequency 적립 빈도(매일/영업일) — 월 평균 일수 환산
 * @param fxRate 기준 환율(외화 1단위당 원, 예: 1380). 0보다 커야 한다
 * @param bufferRate 버퍼율(예: 0.05). 음수 불가 — 운영 상수 app.policy.fx-buffer-rate(구현규칙 6장)
 */
public record FxRecommendationInput(
        BigDecimal unitAmount, FxFrequency frequency, BigDecimal fxRate, BigDecimal bufferRate) {

    public FxRecommendationInput {
        Objects.requireNonNull(unitAmount, "unitAmount");
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(fxRate, "fxRate");
        Objects.requireNonNull(bufferRate, "bufferRate");
        if (unitAmount.signum() <= 0) {
            throw new IllegalArgumentException("unitAmount는 0보다 커야 한다: " + unitAmount);
        }
        if (fxRate.signum() <= 0) {
            throw new IllegalArgumentException("fxRate는 0보다 커야 한다: " + fxRate);
        }
        if (bufferRate.signum() < 0) {
            throw new IllegalArgumentException("bufferRate는 음수일 수 없다: " + bufferRate);
        }
    }
}
