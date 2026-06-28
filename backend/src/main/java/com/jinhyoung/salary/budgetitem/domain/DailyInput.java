package com.jinhyoung.salary.budgetitem.domain;

import java.util.Objects;

/**
 * 일 단위 입력 원본(ITEM-03) — 엔티티가 아닌 순수 값 객체. 매일 적립식 항목의 일 금액(원)과 빈도(매일/영업일)를
 * 담고 월 환산 금액을 계산한다(요구사항정의서 ITEM-03, 구현규칙 1장). 원본은 input_meta로 보존된다(ERD).
 *
 * <p>월 환산 = 일 금액 × 빈도별 월 평균 일수({@link FxFrequency#daysPerMonth()} — DAILY 30 / BUSINESS_DAYS 22).
 * 일 금액은 원 단위 정수라 곱셈만으로 정수가 나와 반올림이 없다(외화 ITEM-04와 달리 환율·버퍼·1,000원 올림 없음).
 * 금액은 long 원(규칙 2) — double/float 미사용. 빈도 enum은 외화 도우미(ITEM-04)와 월 평균 일수 정의를 공유한다.
 *
 * @param dailyAmount 1일 적립 금액(원). 0보다 커야 한다
 * @param frequency 적립 빈도(매일/영업일) — 월 평균 일수 환산
 */
public record DailyInput(long dailyAmount, FxFrequency frequency) {

    public DailyInput {
        Objects.requireNonNull(frequency, "frequency");
        if (dailyAmount <= 0) {
            throw new IllegalArgumentException("dailyAmount는 0보다 커야 한다: " + dailyAmount);
        }
    }

    /** 월 환산 금액(원) = 일 금액 × 빈도별 월 평균 일수. 정수 곱이라 반올림 없음. 오버플로는 예외로 드러낸다. */
    public long toMonthlyAmount() {
        return Math.multiplyExact(dailyAmount, frequency.daysPerMonth());
    }
}
