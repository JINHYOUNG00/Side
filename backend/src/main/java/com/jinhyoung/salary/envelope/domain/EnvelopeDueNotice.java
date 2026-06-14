package com.jinhyoung.salary.envelope.domain;

import java.time.LocalDate;

/**
 * 봉투 지출 시기 알림의 대상 판정(NOTI-02, 요구사항 "지출일 전 알림"). 의존성 없는 순수 계산 — 오늘 기준으로 다음
 * 지출일이 알림 윈도우 안에 드는지만 본다(날짜는 호출자가 주입한 KST {@code Clock}으로 산출 — 규칙 3, 직접 호출 금지).
 *
 * <p>윈도우는 <b>오늘부터 {@link #LEAD_DAYS}일 뒤까지(양끝 포함)</b>다: {@code today ≤ next_due_date ≤
 * today + LEAD_DAYS}. 즉 지출일 D-{@code LEAD_DAYS}부터 당일(D-0)까지 알림 대상이고, 지출일이 이미 지난 봉투
 * (next_due &lt; today)는 "지출일 전"이 아니므로 제외한다. 일일 배치가 이 윈도우 동안 매일 돌아도, 중복 방지
 * 게이트가 대상일=next_due로 키를 잡아 봉투당 1회만 발송한다(규칙 8 멱등).
 */
public final class EnvelopeDueNotice {

    /** 지출일 며칠 전부터 알림을 보낼지(D-LEAD_DAYS ~ D-0, 양끝 포함). */
    public static final int LEAD_DAYS = 3;

    private EnvelopeDueNotice() {
        // 순수 유틸 — 인스턴스화 금지
    }

    /** 알림 윈도우의 마지막 날 = {@code today + LEAD_DAYS}. 리포지토리 범위 조회(between)의 상한으로 쓴다. */
    public static LocalDate windowEnd(LocalDate today) {
        return today.plusDays(LEAD_DAYS);
    }

    /**
     * 다음 지출일이 오늘 기준 알림 윈도우 안인지 — {@code today ≤ nextDueDate ≤ today + LEAD_DAYS}. 지출일 경과분
     * (nextDueDate &lt; today)은 false.
     */
    public static boolean isDueSoon(LocalDate today, LocalDate nextDueDate) {
        return !nextDueDate.isBefore(today) && !nextDueDate.isAfter(windowEnd(today));
    }
}
