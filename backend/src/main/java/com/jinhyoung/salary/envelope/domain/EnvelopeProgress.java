package com.jinhyoung.salary.envelope.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 봉투 적립 진행률·D-day 계산 — 의존성 없는 순수 클래스(ENV-03, ArchUnit 규칙 9).
 *
 * <p>봉투 조회 응답에 노출하는 두 표시값을 산술로만 만든다(월 적립액은 별도 {@link EnvelopeAccrual} 소관):
 *
 * <ul>
 *   <li><b>진행률(%)</b> = {@code saved ÷ target × 100} — 금액은 long 원 단위, 중간 계산은 BigDecimal(규칙 2).
 *   <li><b>D-day</b> = {@code next_due_date − 오늘}(KST 기준 일수) — 호출자가 주입한 {@code Clock}으로 산출한 오늘(규칙 3).
 * </ul>
 */
public final class EnvelopeProgress {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private EnvelopeProgress() {}

    /**
     * 적립 진행률(%)을 정수로 계산한다.
     *
     * <p><b>내림(FLOOR)</b>으로 처리한다 — 목표를 실제로 충족하기 전에는 반올림으로 100%가 떠 "다 모았다"처럼
     * 보이는 것을 막는다(예: 99.6% → 99). 적립액이 목표 이상이면 100을 돌려준다.
     *
     * @param savedAmount 현재 적립액(원, ≥ 0)
     * @param targetAmount 목표 금액(원, &gt; 0 — 검증으로 보장)
     * @return 0~100 사이 진행률(%)
     */
    public static int progressPercent(long savedAmount, long targetAmount) {
        if (targetAmount <= 0) {
            throw new IllegalArgumentException("목표 금액은 0보다 커야 한다: " + targetAmount);
        }
        if (savedAmount <= 0) {
            return 0;
        }
        if (savedAmount >= targetAmount) {
            return 100;
        }
        return BigDecimal.valueOf(savedAmount)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(targetAmount), 0, RoundingMode.DOWN)
                .intValueExact();
    }

    /**
     * D-day = {@code nextDueDate − today}(일). 0이면 당일, 양수면 남은 일수, 음수면 지출일이 지났음을 뜻한다.
     *
     * <p>생성·수정 시 {@code next_due_date ≥ 오늘}을 강제하지만(구현규칙 5장), 시간이 흐르면 지출일이 지난
     * 봉투가 생길 수 있어 음수도 그대로 돌려준다(주기 갱신 ENV-05 전까지의 표시값).
     *
     * @param today 기준일(호출자가 주입한 KST {@code Clock}으로 산출 — 규칙 3)
     * @param nextDueDate 봉투의 다음 지출일
     */
    public static long dDay(LocalDate today, LocalDate nextDueDate) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(nextDueDate, "nextDueDate");
        return ChronoUnit.DAYS.between(today, nextDueDate);
    }
}
