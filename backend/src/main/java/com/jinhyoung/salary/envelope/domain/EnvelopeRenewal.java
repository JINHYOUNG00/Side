package com.jinhyoung.salary.envelope.domain;

import java.time.LocalDate;

/**
 * 봉투 주기 갱신 규칙(ENV-05, 요구사항정의서 2.6). 의존성 없는 순수 클래스 — 반복 주기(cycleMonths)와 현재
 * 다음 지출일만으로 "지출 후 봉투가 어떻게 다음 주기로 넘어가는가"를 계산한다(규칙 9, 금액 없음).
 *
 * <p>의미론:
 *
 * <ul>
 *   <li><b>반복형</b>(cycleMonths != null) — 다음 지출일을 주기(개월)만큼 뒤로 이동하고 적립을 다시 시작한다. 봉투는
 *       ACTIVE로 남는다. 적립 캐시(saved_amount)는 ENV-04 지출 처리가 이미 정한 값을 그대로 둔다 — 잉여 이월
 *       (carryOver=true)이면 잔여가 다음 주기 시작 적립으로 보존되고, 회수·부족·정확이면 이미 0이다(owner 결정).
 *       "적립 재시작"은 월 적립액({@link EnvelopeAccrual})이 이동한 다음 지출일을 기준으로 자동 재계산되는 것으로
 *       성립한다 — saved_amount를 강제로 0으로 만들지 않는다.
 *   <li><b>일회성</b>(cycleMonths == null) — 다음 주기가 없으므로 종료(CLOSED) 처리한다.
 * </ul>
 *
 * <p>다음 지출일은 <b>현재 다음 지출일</b>을 기준으로 이동한다(지출일이 아니라) — 분기·연 단위 봉투가 달력에
 * 고정(예: 매년 1/10)되도록 한다. 월말 날짜는 자바 {@link LocalDate#plusMonths}의 말일 보정을 따른다(예:
 * 1/31 + 1개월 = 2/28).
 */
public final class EnvelopeRenewal {

    private EnvelopeRenewal() {}

    /** 반복형 봉투인지 — 반복 주기(개월)가 있으면 true, 일회성(null)이면 false. */
    public static boolean isRecurring(Short cycleMonths) {
        return cycleMonths != null;
    }

    /**
     * 지출 후 이동할 다음 지출일 = 현재 다음 지출일 + 주기(개월). 반복형에만 의미가 있다. 월말은 자바 표준
     * {@code plusMonths}의 말일 보정을 따른다.
     */
    public static LocalDate nextDueAfterRenewal(LocalDate currentNextDue, int cycleMonths) {
        if (cycleMonths < 1) {
            throw new IllegalArgumentException("cycleMonths must be at least 1: " + cycleMonths);
        }
        return currentNextDue.plusMonths(cycleMonths);
    }
}
